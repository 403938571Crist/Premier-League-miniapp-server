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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fabrizio Romano 资讯源 - 高频源
 * 使用 RSS.app 或 nitter 等第三方服务抓取
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RomanoProvider implements NewsProvider {
    
    private final HttpClientUtil httpClient;
    private final ContentCleanService contentCleanService;
    private final ObjectMapper objectMapper;
    
    // nitter 实例（Twitter 镜像）- 作为备用方案
    private static final String NITTER_URL = "https://nitter.net/FabrizioRomano/rss";
    
    @Override
    public String getSourceType() {
        return "romano";
    }
    
    @Override
    public String getSourceName() {
        return "Fabrizio Romano";
    }
    
    @Override
    public List<News> fetchLatest(int maxItems) {
        log.info("[Romano] Fetching updates...");
        
        // 检查是否有 X API Token
        String token = System.getenv("X_BEARER_TOKEN");
        if (token != null && !token.isBlank()) {
            return fetchFromXApi(token, maxItems);
        }
        
        // 备用：尝试 RSS 源
        return fetchFromRss(maxItems);
    }
    
    private List<News> fetchFromXApi(String token, int maxItems) {
        try {
            // X API v2 搜索推文
            String url = "https://api.twitter.com/2/tweets/search/recent?query=from:FabrizioRomano&max_results=" + maxItems;
            
            String response = httpClient.getWithAuth(url, "Bearer " + token);
            if (response == null) {
                log.warn("[Romano] X API returned empty response");
                return Collections.emptyList();
            }
            
            return parseXApiResponse(response);
            
        } catch (Exception e) {
            log.error("[Romano] X API fetch failed", e);
            return Collections.emptyList();
        }
    }
    
    private List<News> fetchFromRss(int maxItems) {
        try {
            // 尝试使用 RSSHub 或类似服务
            // 注意：这些服务可能不稳定，需要定期检查和更换
            String[] rssUrls = {
                "https://rsshub.app/twitter/user/FabrizioRomano",
                "https://nitter.privacydev.net/FabrizioRomano/rss",
            };
            
            for (String rssUrl : rssUrls) {
                try {
                    String xml = httpClient.get(rssUrl);
                    if (xml != null && xml.contains("<item")) {
                        log.info("[Romano] Successfully fetched from RSS: {}", rssUrl);
                        return parseRss(xml, maxItems);
                    }
                } catch (Exception e) {
                    log.warn("[Romano] RSS source failed: {}", rssUrl);
                }
            }
            
            log.warn("[Romano] All RSS sources failed");
            return Collections.emptyList();
            
        } catch (Exception e) {
            log.error("[Romano] RSS fetch failed", e);
            return Collections.emptyList();
        }
    }
    
    private List<News> parseXApiResponse(String json) {
        List<News> newsList = new ArrayList<>();
        
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            
            if (data.isArray()) {
                for (JsonNode tweet : data) {
                    News news = parseTweet(tweet);
                    if (news != null) {
                        newsList.add(news);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[Romano] Failed to parse X API response", e);
        }
        
        return newsList;
    }
    
    private News parseTweet(JsonNode tweet) {
        try {
            String id = tweet.path("id").asText();
            String text = tweet.path("text").asText();
            
            if (id.isEmpty() || text.isEmpty()) {
                return null;
            }
            
            // 过滤非英超相关内容
            if (!isPremierLeagueRelated(text)) {
                return null;
            }
            
            String url = "https://twitter.com/FabrizioRomano/status/" + id;
            
            News news = News.builder()
                    .id("romano-" + id)
                    .title(text.substring(0, Math.min(text.length(), 100)) + (text.length() > 100 ? "..." : ""))
                    .summary(text.substring(0, Math.min(text.length(), 2000)))
                    .source("X (Twitter)")
                    .sourceType("romano")
                    .mediaType("social")
                    .sourcePublishedAt(LocalDateTime.now())
                    .url(url)
                    .coverImage("")
                    .author("Fabrizio Romano")
                    .build();
            
            news.setTags(contentCleanService.extractTags(text, ""));
            news.setHotScore(90); // Romano 的转会消息热度较高
            
            return news;
            
        } catch (Exception e) {
            log.error("[Romano] Failed to parse tweet", e);
            return null;
        }
    }
    
    private List<News> parseRss(String xml, int maxItems) {
        List<News> newsList = new ArrayList<>();
        
        try {
            // 简单的 RSS 解析
            String[] items = xml.split("<item>");
            
            for (int i = 1; i < Math.min(items.length, maxItems + 1); i++) {
                String item = items[i];
                
                String title = extractTag(item, "title");
                String description = extractTag(item, "description");
                String link = extractTag(item, "link");
                String pubDate = extractTag(item, "pubDate");
                
                String content = title + " " + description;
                
                // 过滤非英超相关内容
                if (!isPremierLeagueRelated(content)) {
                    continue;
                }
                
                News news = News.builder()
                        .id("romano-" + link.hashCode())
                        .title(title.isEmpty() ? description.substring(0, Math.min(100, description.length())) : title)
                        .summary(description.substring(0, Math.min(description.length(), 2000)))
                        .source("X (Twitter)")
                        .sourceType("romano")
                        .mediaType("social")
                        .sourcePublishedAt(LocalDateTime.now())
                        .url(link)
                        .coverImage("")
                        .author("Fabrizio Romano")
                        .build();
                
                news.setTags(contentCleanService.extractTags(content, ""));
                news.setHotScore(90);
                
                newsList.add(news);
            }
            
        } catch (Exception e) {
            log.error("[Romano] Failed to parse RSS", e);
        }
        
        return newsList;
    }
    
    private String extractTag(String xml, String tagName) {
        String startTag = "<" + tagName + ">";
        String endTag = "</" + tagName + ">";
        int start = xml.indexOf(startTag);
        int end = xml.indexOf(endTag);
        
        if (start != -1 && end != -1 && start < end) {
            String content = xml.substring(start + startTag.length(), end);
            // 移除 CDATA
            content = content.replace("<![CDATA[", "").replace("]]>", "");
            // 移除 HTML 标签
            content = content.replaceAll("<[^>]+>", "");
            return content.trim();
        }
        return "";
    }
    
    private boolean isPremierLeagueRelated(String text) {
        String lowerText = text.toLowerCase();
        String[] keywords = {"premier league", "arsenal", "liverpool", "man city", "man utd", 
                "chelsea", "tottenham", "spurs", "newcastle", "brighton", "aston villa", 
                "west ham", "brentford", "crystal palace", "everton", "fulham", 
                "nottingham forest", "wolves", "burnley", "luton", "sheffield united",
                "英超", "阿森纳", "曼城", "利物浦", "曼联", "切尔西", "热刺"};
        
        for (String kw : keywords) {
            if (lowerText.contains(kw.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    public boolean isAvailable() {
        // 检查 X API Token 或 RSS 是否可用
        String token = System.getenv("X_BEARER_TOKEN");
        if (token != null && !token.isBlank()) {
            return true;
        }
        
        // 尝试 RSS
        try {
            String xml = httpClient.get("https://rsshub.app/twitter/user/FabrizioRomano");
            return xml != null && xml.contains("<item");
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public String getFrequencyLevel() {
        return "high";
    }
}
