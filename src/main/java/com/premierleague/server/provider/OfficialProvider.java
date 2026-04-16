package com.premierleague.server.provider;

import com.premierleague.server.entity.News;
import com.premierleague.server.service.ContentCleanService;
import com.premierleague.server.util.HttpClientUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 英超官方资讯源 - 中频源
 * 抓取英超官网 RSS
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfficialProvider implements NewsProvider {
    
    private final HttpClientUtil httpClient;
    private final ContentCleanService contentCleanService;
    
    // BBC Sport 英超 RSS（英超官网 RSS 已不可用）
    private static final String RSS_URL = "https://feeds.bbci.co.uk/sport/football/rss.xml";
    
    @Override
    public String getSourceType() {
        return "official";
    }
    
    @Override
    public String getSourceName() {
        return "英超官方";
    }
    
    @Override
    public List<News> fetchLatest(int maxItems) {
        log.info("[Official] Fetching from RSS: {}", RSS_URL);
        
        try {
            String xml = httpClient.get(RSS_URL);
            if (xml == null || xml.isEmpty()) {
                log.warn("[Official] Empty RSS response");
                return Collections.emptyList();
            }
            
            return parseRss(xml, maxItems);
        } catch (Exception e) {
            log.error("[Official] Failed to fetch RSS", e);
            return Collections.emptyList();
        }
    }
    
    private List<News> parseRss(String xml, int maxItems) {
        List<News> newsList = new ArrayList<>();
        
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
            NodeList items = doc.getElementsByTagName("item");
            
            for (int i = 0; i < Math.min(items.getLength(), maxItems); i++) {
                Element item = (Element) items.item(i);
                News news = parseItem(item);
                if (news != null) {
                    newsList.add(news);
                }
            }
            
            log.info("[Official] Parsed {} items from RSS", newsList.size());
            
        } catch (Exception e) {
            log.error("[Official] Failed to parse RSS", e);
        }
        
        return newsList;
    }
    
    private News parseItem(Element item) {
        try {
            String title = getTextContent(item, "title");
            String description = getTextContent(item, "description");
            String link = getTextContent(item, "link");
            String pubDate = getTextContent(item, "pubDate");
            String guid = getTextContent(item, "guid");
            
            if (title.isEmpty() || link.isEmpty()) {
                log.warn("[Official] Missing required fields");
                return null;
            }
            
            // 过滤非英超内容
            String content = title + " " + description;
            if (!isPremierLeagueRelated(content)) {
                log.debug("[Official] Skipping non-PL article: {}", title.substring(0, Math.min(40, title.length())));
                return null;
            }
            
            // 解析发布时间
            LocalDateTime publishTime = parsePubDate(pubDate);
            
            // 使用 guid 或 link 作为 ID
            String id = guid.isEmpty() ? String.valueOf(link.hashCode()) : guid;
            
            News news = News.builder()
                    .id("pl-official-" + id.hashCode())
                    .title(title)
                    .summary(description.isEmpty() ? title : description.substring(0, Math.min(description.length(), 2000)))
                    .source("英超官网")
                    .sourceType("official")
                    .mediaType("article")
                    .sourcePublishedAt(publishTime)
                    .url(link)
                    .coverImage("")
                    .author("Premier League")
                    .build();
            
            news.setTags(contentCleanService.extractTags(title, description));
            news.setHotScore(contentCleanService.calculateDefaultHotScore(news));
            
            return news;
            
        } catch (Exception e) {
            log.error("[Official] Failed to parse item", e);
            return null;
        }
    }
    
    private String getTextContent(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }
        return "";
    }
    
    private LocalDateTime parsePubDate(String pubDate) {
        try {
            // RSS 日期格式: "Mon, 12 Apr 2026 14:30:00 GMT"
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z");
            return LocalDateTime.parse(pubDate, formatter);
        } catch (Exception e) {
            try {
                // 尝试其他格式
                DateTimeFormatter formatter = DateTimeFormatter.RFC_1123_DATE_TIME;
                return LocalDateTime.parse(pubDate, formatter);
            } catch (Exception e2) {
                log.warn("[Official] Failed to parse pubDate: {}, using now", pubDate);
                return LocalDateTime.now();
            }
        }
    }
    
    private boolean isPremierLeagueRelated(String text) {
        String lower = text.toLowerCase();
        String[] keywords = {
            "premier league", "premierleague", "epl",
            "arsenal", "liverpool", "manchester city", "man city",
            "manchester united", "man utd", "chelsea", "tottenham", "spurs",
            "newcastle", "brighton", "aston villa", "west ham",
            "brentford", "crystal palace", "everton", "fulham",
            "nottingham forest", "wolves", "wolverhampton",
            "burnley", "luton", "sheffield united"
        };
        
        for (String kw : keywords) {
            if (lower.contains(kw)) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    public boolean isAvailable() {
        try {
            String response = httpClient.get(RSS_URL);
            return response != null && response.contains("<rss");
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public String getFrequencyLevel() {
        return "medium";
    }
}
