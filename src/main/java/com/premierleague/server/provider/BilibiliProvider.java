package com.premierleague.server.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.premierleague.server.entity.News;
import com.premierleague.server.service.ContentCleanService;
import com.premierleague.server.util.HttpClientUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Bilibili 资讯源 - 低频源
 * 抓取英超相关视频内容
 * 
 * API: https://api.bilibili.com/x/web-interface/search/type
 * 搜索关键词: 英超
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BilibiliProvider implements NewsProvider {
    
    private final HttpClientUtil httpClient;
    private final ContentCleanService contentCleanService;
    private final ObjectMapper objectMapper;
    
    // B站搜索API
    private static final String SEARCH_API = "https://api.bilibili.com/x/web-interface/search/type";
    
    @Override
    public String getSourceType() {
        return "bilibili";
    }
    
    @Override
    public String getSourceName() {
        return "B站足球";
    }
    
    @Override
    public List<News> fetchLatest(int maxItems) {
        log.info("[Bilibili] Fetching from search API");
        
        List<News> allNews = new ArrayList<>();
        
        // 搜索英超相关视频
        String[] keywords = {"英超", "阿森纳", "曼城", "利物浦", "曼联", "切尔西"};
        
        for (String keyword : keywords) {
            if (allNews.size() >= maxItems) break;
            
            try {
                String url = buildSearchUrl(keyword, 1);
                String response = httpClient.get(url);
                
                if (response != null) {
                    List<News> videos = parseSearchResponse(response);
                    allNews.addAll(videos);
                    log.info("[Bilibili] Keyword '{}' got {} videos", keyword, videos.size());
                }
                
                Thread.sleep(1000); // polite delay
                
            } catch (Exception e) {
                log.error("[Bilibili] Failed to search keyword: {}", keyword, e);
            }
        }
        
        // 去重并按时间排序
        return allNews.stream()
                .distinct()
                .sorted((a, b) -> b.getSourcePublishedAt().compareTo(a.getSourcePublishedAt()))
                .limit(maxItems)
                .toList();
    }
    
    private String buildSearchUrl(String keyword, int page) {
        return String.format("%s?keyword=%s&search_type=video&page=%d&order=pubdate",
                SEARCH_API,
                java.net.URLEncoder.encode(keyword, java.nio.charset.StandardCharsets.UTF_8),
                page);
    }
    
    private List<News> parseSearchResponse(String json) {
        List<News> newsList = new ArrayList<>();
        
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            JsonNode result = data.path("result");
            
            if (result.isArray()) {
                for (JsonNode video : result) {
                    News news = parseVideo(video);
                    if (news != null) {
                        newsList.add(news);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[Bilibili] Failed to parse response", e);
        }
        
        return newsList;
    }
    
    private News parseVideo(JsonNode video) {
        try {
            String bvid = video.path("bvid").asText();
            String title = video.path("title").asText();
            String description = video.path("description").asText();
            String pubdate = video.path("pubdate").asText();
            String pic = video.path("pic").asText();
            String author = video.path("author").asText();
            String link = "https://www.bilibili.com/video/" + bvid;
            
            // 清理标题中的HTML标签
            title = title.replaceAll("<[^>]+>", "");
            
            // 只保留最近7天的视频
            LocalDateTime publishTime = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochSecond(Long.parseLong(pubdate)),
                    ZoneId.systemDefault());
            
            if (publishTime.isBefore(LocalDateTime.now().minusDays(7))) {
                return null;
            }
            
            News news = News.builder()
                    .id("bili-" + bvid)
                    .title(title)
                    .summary(contentCleanService.cleanText(description, 500))
                    .source(author)
                    .sourceType("bilibili")
                    .mediaType("video-summary")
                    .sourcePublishedAt(publishTime)
                    .url(link)
                    .coverImage("https:" + pic)
                    .author(author)
                    .build();
            
            // 提取标签
            news.setTags(contentCleanService.extractTags(title, description));
            news.setHotScore(calculateHotScore(video));
            
            return news;
            
        } catch (Exception e) {
            log.error("[Bilibili] Failed to parse video", e);
            return null;
        }
    }
    
    private Integer calculateHotScore(JsonNode video) {
        int score = 40; // 视频基础分较低
        
        try {
            int view = video.path("play").asInt();
            int like = video.path("like").asInt();
            
            // 播放量和点赞加权
            score += Math.min(view / 10000, 20);
            score += Math.min(like / 1000, 10);
            
        } catch (Exception ignored) {}
        
        return Math.min(score, 100);
    }
    
    @Override
    public boolean isAvailable() {
        String response = httpClient.get(SEARCH_API + "?keyword=%E8%8B%B1%E8%B6%85&search_type=video&page=1");
        return response != null && response.contains("result");
    }
    
    @Override
    public String getFrequencyLevel() {
        return "low";
    }
}
