package com.premierleague.server.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.premierleague.server.entity.News;
import com.premierleague.server.service.ContentCleanService;
import com.premierleague.server.util.HttpClientUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Reddit r/PremierLeague 社区热帖 - 低频源
 * API: https://www.reddit.com/r/PremierLeague/hot.json（无需认证）
 *
 * 过滤规则：
 *  - 仅保留 score >= MIN_SCORE 的帖子（社区认可度）
 *  - 排除 stickied（置顶公告）
 *  - 排除媒体类外链（视频/图集）→ mediaType = social
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedditProvider implements NewsProvider {

    private final HttpClientUtil httpClient;
    private final ContentCleanService contentCleanService;
    private final ObjectMapper objectMapper;

    private static final String API_URL =
            "https://www.reddit.com/r/PremierLeague/hot.json?limit=50&raw_json=1";
    /** Reddit 要求标识请求来源的 User-Agent */
    private static final String REDDIT_UA =
            "PremierLeagueMiniapp/1.0 (aggregator; non-commercial)";
    /** 最低热度门槛（赞数）*/
    private static final int MIN_SCORE = 50;

    @Override
    public String getSourceType() {
        return "reddit";
    }

    @Override
    public String getSourceName() {
        return "Reddit 社区";
    }

    @Override
    public List<News> fetchLatest(int maxItems) {
        log.info("[Reddit] Fetching hot posts from r/PremierLeague");
        try {
            String json = httpClient.getWithHeaders(API_URL, Map.of(
                    "User-Agent", REDDIT_UA,
                    "Accept", "application/json"
            ));
            if (json == null || json.isBlank()) {
                log.warn("[Reddit] Empty response");
                return Collections.emptyList();
            }
            return parsePosts(json, maxItems);
        } catch (Exception e) {
            log.error("[Reddit] Failed to fetch posts", e);
            return Collections.emptyList();
        }
    }

    private List<News> parsePosts(String json, int maxItems) {
        List<News> newsList = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode children = root.path("data").path("children");

            for (JsonNode child : children) {
                if (newsList.size() >= maxItems) break;

                JsonNode post = child.path("data");
                News news = parsePost(post);
                if (news != null) {
                    newsList.add(news);
                }
            }
            log.info("[Reddit] Parsed {} posts (score >= {})", newsList.size(), MIN_SCORE);
        } catch (Exception e) {
            log.error("[Reddit] Failed to parse JSON", e);
        }
        return newsList;
    }

    private News parsePost(JsonNode post) {
        try {
            // 跳过置顶公告
            if (post.path("stickied").asBoolean(false)) return null;

            String title = post.path("title").asText("").trim();
            String id = post.path("id").asText("");
            String permalink = "https://www.reddit.com" + post.path("permalink").asText("");
            int score = post.path("score").asInt(0);
            int numComments = post.path("num_comments").asInt(0);
            long createdUtc = post.path("created_utc").asLong(0);
            String selftext = post.path("selftext").asText("").trim();
            String thumbnail = post.path("thumbnail").asText("");
            String postHint = post.path("post_hint").asText("");
            String author = "u/" + post.path("author").asText("unknown");
            String flair = post.path("link_flair_text").asText("");

            if (title.isEmpty() || id.isEmpty()) return null;
            if (score < MIN_SCORE) return null;

            // 过滤不适合聚合的类型（纯图片投票帖、GIF）
            if ("image".equals(postHint) && selftext.isEmpty()) return null;

            // 组装摘要：selftext 优先，否则用 flair + 评论数描述
            String summary = buildSummary(selftext, flair, score, numComments);

            // 封面图：优先使用 preview 中的图，其次 thumbnail
            String coverImage = extractCoverImage(post, thumbnail);

            LocalDateTime publishTime = createdUtc > 0
                    ? LocalDateTime.ofInstant(Instant.ofEpochSecond(createdUtc), ZoneOffset.UTC)
                    : LocalDateTime.now();

            // 媒体类型：带外链 → link；纯文字讨论 → social
            String mediaType = (postHint.equals("link") && !post.path("url").asText("").contains("reddit.com"))
                    ? "link" : "social";

            News news = News.builder()
                    .id("reddit-" + id)
                    .title(title)
                    .summary(summary)
                    .source("Reddit / r/PremierLeague")
                    .sourceType("reddit")
                    .mediaType(mediaType)
                    .sourcePublishedAt(publishTime)
                    .url(permalink)
                    .coverImage(coverImage)
                    .author(author)
                    .sourceNote(flair.isEmpty() ? null : flair)
                    .build();

            news.setTags(contentCleanService.extractTags(title, selftext));
            news.setHotScore(calculateRedditHotScore(score, numComments));
            return news;
        } catch (Exception e) {
            log.error("[Reddit] Failed to parse post", e);
            return null;
        }
    }

    private String buildSummary(String selftext, String flair, int score, int numComments) {
        if (!selftext.isEmpty()) {
            return selftext.substring(0, Math.min(selftext.length(), 500));
        }
        StringBuilder sb = new StringBuilder();
        if (!flair.isEmpty()) sb.append("[").append(flair).append("] ");
        sb.append("↑").append(score).append(" · ").append(numComments).append(" 条评论");
        return sb.toString();
    }

    private String extractCoverImage(JsonNode post, String thumbnail) {
        // 优先取 preview.images[0].source.url（高清）
        JsonNode preview = post.path("preview").path("images");
        if (preview.isArray() && preview.size() > 0) {
            String url = preview.get(0).path("source").path("url").asText("");
            if (!url.isEmpty()) {
                // Reddit 会对 URL 进行 HTML entity encoding，需要解码
                return url.replace("&amp;", "&");
            }
        }
        // 兜底使用 thumbnail（只保留 https 开头的有效地址）
        if (thumbnail.startsWith("https://")) return thumbnail;
        return "";
    }

    /**
     * Reddit 热度算法：赞数 + 评论数加权，最高 100
     */
    private int calculateRedditHotScore(int score, int numComments) {
        int base = 40;
        int upvoteBonus = Math.min(score / 100, 40);       // 每100赞+1，最高+40
        int commentBonus = Math.min(numComments / 20, 20); // 每20评论+1，最高+20
        return Math.min(base + upvoteBonus + commentBonus, 100);
    }

    @Override
    public boolean isAvailable() {
        try {
            String resp = httpClient.getWithHeaders(API_URL, Map.of("User-Agent", REDDIT_UA));
            return resp != null && resp.contains("\"kind\"");
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getFrequencyLevel() {
        return "low";
    }
}
