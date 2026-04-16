package com.premierleague.server.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.premierleague.server.entity.News;
import com.premierleague.server.service.ContentCleanService;
import com.premierleague.server.util.HttpClientUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * X(Twitter) 资讯源 - 高频源
 * 抓取 Fabrizio Romano、英超官方账号等
 * 
 * 需要配置: app.x.bearer-token
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XProvider implements NewsProvider {
    
    private final HttpClientUtil httpClient;
    private final ContentCleanService contentCleanService;
    private final ObjectMapper objectMapper;
    
    @Value("${app.x.bearer-token:}")
    private String bearerToken;
    
    // 重点关注的账号ID (Twitter User IDs)
    private static final List<String> WATCH_USER_IDS = List.of(
            "330262748",    // FabrizioRomano
            "343627165",    // premierleague
            "36656337",     // BBCMOTD
            "27685093"      // ESPNFC
    );
    
    private static final String API_URL = "https://api.twitter.com/2/tweets/search/recent";
    
    @Override
    public String getSourceType() {
        return "x";
    }
    
    @Override
    public String getSourceName() {
        return "X (Twitter)";
    }
    
    @Override
    public List<News> fetchLatest(int maxItems) {
        if (!isAvailable()) {
            log.warn("[XProvider] Bearer token not configured, skipping fetch");
            return List.of();
        }
        
        log.info("[XProvider] Fetching tweets from X API");
        
        List<News> allNews = new ArrayList<>();
        
        for (String userId : WATCH_USER_IDS) {
            try {
                String query = buildQuery(userId);
                String response = fetchFromApi(query, maxItems / WATCH_USER_IDS.size());
                
                if (response != null) {
                    List<News> userNews = parseResponse(response);
                    allNews.addAll(userNews);
                    log.info("[XProvider] Fetched {} tweets from user {}", userNews.size(), userId);
                }
                
                // 避免触发速率限制
                Thread.sleep(1000);
                
            } catch (Exception e) {
                log.error("[XProvider] Failed to fetch from user {}", userId, e);
            }
        }
        
        // 按时间排序
        allNews.sort((a, b) -> b.getSourcePublishedAt().compareTo(a.getSourcePublishedAt()));
        
        return allNews.stream().limit(maxItems).toList();
    }
    
    private String buildQuery(String userId) {
        // 从特定用户获取英超相关推文
        return String.format("(from:%s) (PremierLeague OR 英超 OR transfer OR 转会) -is:retweet", userId);
    }
    
    private String fetchFromApi(String query, int maxResults) {
        try {
            String url = String.format("%s?query=%s&max_results=%d&tweet.fields=created_at,author_id,public_metrics", 
                    API_URL, 
                    java.net.URLEncoder.encode(query, "UTF-8"),
                    maxResults);
            
            return httpClient.getWithAuth(url, "Bearer " + bearerToken);
            
        } catch (Exception e) {
            log.error("[XProvider] API request failed", e);
            return null;
        }
    }
    
    private List<News> parseResponse(String json) {
        List<News> newsList = new ArrayList<>();
        
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.get("data");
            
            if (data != null && data.isArray()) {
                for (JsonNode tweet : data) {
                    News news = parseTweet(tweet);
                    if (news != null) {
                        newsList.add(news);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[XProvider] Failed to parse response", e);
        }
        
        return newsList;
    }
    
    private News parseTweet(JsonNode tweet) {
        try {
            String text = tweet.get("text").asText();
            String id = tweet.get("id").asText();
            String createdAt = tweet.get("created_at").asText();
            
            // 过滤非英超相关内容
            if (!isRelevant(text)) {
                return null;
            }
            
            News news = News.builder()
                    .title(text.substring(0, Math.min(100, text.length())) + "...")
                    .summary(text)
                    .source("X")
                    .sourceType("x")
                    .mediaType("social")
                    .sourcePublishedAt(LocalDateTime.parse(createdAt, DateTimeFormatter.ISO_DATE_TIME))
                    .url("https://x.com/i/web/status/" + id)
                    .author(getAuthorName(tweet))
                    .hotScore(calculateHotScore(tweet))
                    .build();
            
            // 提取标签
            news.setTags(contentCleanService.extractTags(news.getTitle(), text));
            
            return news;
            
        } catch (Exception e) {
            log.error("[XProvider] Failed to parse tweet", e);
            return null;
        }
    }
    
    private boolean isRelevant(String text) {
        String lower = text.toLowerCase();
        return lower.contains("premier") || 
               lower.contains("arsenal") || lower.contains("liverpool") || 
               lower.contains("man utd") || lower.contains("man city") || 
               lower.contains("chelsea") || lower.contains("tottenham") ||
               text.contains("英超") || text.contains("转会");
    }
    
    private String getAuthorName(JsonNode tweet) {
        // 实际应该从 includes.users 获取
        return "X User";
    }
    
    private Integer calculateHotScore(JsonNode tweet) {
        int score = 50;
        
        JsonNode metrics = tweet.get("public_metrics");
        if (metrics != null) {
            int retweets = metrics.get("retweet_count").asInt();
            int likes = metrics.get("like_count").asInt();
            
            score += Math.min(retweets / 10, 20);
            score += Math.min(likes / 50, 15);
        }
        
        return Math.min(score, 100);
    }
    
    @Override
    public boolean isAvailable() {
        return bearerToken != null && !bearerToken.isEmpty();
    }
    
    @Override
    public String getFrequencyLevel() {
        return "high";
    }
}
