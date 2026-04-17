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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The Guardian 英超资讯 - 中频源
 * RSS: https://www.theguardian.com/football/premier-league/rss
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuardianProvider implements NewsProvider {

    private final HttpClientUtil httpClient;
    private final ContentCleanService contentCleanService;

    private static final String RSS_URL = "https://www.theguardian.com/football/premierleague/rss";
    private static final DateTimeFormatter RFC1123 =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);

    @Override
    public String getSourceType() {
        return "guardian";
    }

    @Override
    public String getSourceName() {
        return "The Guardian";
    }

    @Override
    public List<News> fetchLatest(int maxItems) {
        log.info("[Guardian] Fetching from RSS: {}", RSS_URL);
        try {
            String xml = httpClient.get(RSS_URL);
            if (xml == null || xml.isBlank()) {
                log.warn("[Guardian] Empty RSS response");
                return Collections.emptyList();
            }
            return parseRss(xml, maxItems);
        } catch (Exception e) {
            log.error("[Guardian] Failed to fetch RSS", e);
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
            log.info("[Guardian] Parsed {} items", newsList.size());
        } catch (Exception e) {
            log.error("[Guardian] Failed to parse RSS", e);
        }
        return newsList;
    }

    private News parseItem(Element item) {
        try {
            String title = getText(item, "title");
            String description = getText(item, "description");
            String link = getText(item, "link");
            String pubDate = getText(item, "pubDate");
            String guid = getText(item, "guid");
            // Guardian RSS 包含 dc:creator 作者字段
            String author = getText(item, "dc:creator");
            if (author.isEmpty()) author = "The Guardian";

            if (title.isEmpty() || link.isEmpty()) {
                return null;
            }

            // Guardian PL RSS 已按英超分类，但仍可能混入其他联赛内容做一次二次过滤
            if (isNonPLContent(title + " " + description)) {
                log.debug("[Guardian] Skipping non-PL: {}", title.substring(0, Math.min(50, title.length())));
                return null;
            }

            // Guardian RSS 的 <media:content> 封面图
            String coverImage = extractMediaUrl(item);

            LocalDateTime publishTime = parsePubDate(pubDate);
            String idBase = guid.isEmpty() ? link : guid;

            News news = News.builder()
                    .id("guardian-" + Math.abs(idBase.hashCode()))
                    .title(title)
                    .summary(cleanDescription(description))
                    .source("The Guardian")
                    .sourceType("guardian")
                    .mediaType(detectType(title))
                    .sourcePublishedAt(publishTime)
                    .url(link)
                    .coverImage(coverImage)
                    .author(author)
                    .build();

            news.setTags(contentCleanService.extractTags(title, description));
            news.setHotScore(contentCleanService.calculateDefaultHotScore(news));
            return news;
        } catch (Exception e) {
            log.error("[Guardian] Failed to parse item", e);
            return null;
        }
    }

    /**
     * 过滤明显非英超的内容（如欧冠、国家队比赛）
     * Guardian PL RSS 里偶尔混入欧冠 / 世界杯内容
     */
    private boolean isNonPLContent(String text) {
        String lower = text.toLowerCase();
        // 如果内容明确标注其他联赛且没有英超球队，则跳过
        boolean hasOtherLeague = lower.contains("champions league final")
                || lower.contains("world cup final")
                || lower.contains("serie a")
                || lower.contains("bundesliga")
                || lower.contains("la liga");
        if (!hasOtherLeague) return false;

        // 即使包含其他联赛词，只要有英超球队就保留
        String[] plTeams = {
            "arsenal", "liverpool", "manchester city", "man city",
            "manchester united", "man utd", "chelsea", "tottenham", "spurs",
            "newcastle", "brighton", "aston villa", "west ham", "brentford",
            "crystal palace", "everton", "fulham", "nottingham forest",
            "wolves", "wolverhampton", "bournemouth", "ipswich",
            "leicester", "southampton"
        };
        for (String team : plTeams) {
            if (lower.contains(team)) return false;
        }
        return true;
    }

    private String detectType(String title) {
        String lower = title.toLowerCase();
        if (lower.contains("transfer") || lower.contains("signing") || lower.contains("sign")) {
            return "transfer";
        }
        if (lower.contains("interview") || lower.contains("exclusive")) {
            return "article";
        }
        return "article";
    }

    private String cleanDescription(String raw) {
        if (raw == null || raw.isBlank()) return "";
        // Guardian 的 description 有时包含 HTML
        String cleaned = raw.replaceAll("<[^>]+>", "").trim();
        return cleaned.substring(0, Math.min(cleaned.length(), 2000));
    }

    private String extractMediaUrl(Element item) {
        NodeList mediaNodes = item.getElementsByTagNameNS("http://search.yahoo.com/mrss/", "content");
        if (mediaNodes.getLength() > 0) {
            String url = ((Element) mediaNodes.item(0)).getAttribute("url");
            if (!url.isEmpty()) return url;
        }
        return "";
    }

    private String getText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        return nodes.getLength() > 0 ? nodes.item(0).getTextContent().trim() : "";
    }

    private LocalDateTime parsePubDate(String pubDate) {
        if (pubDate == null || pubDate.isBlank()) return LocalDateTime.now();
        try {
            return ZonedDateTime.parse(pubDate, RFC1123).toLocalDateTime();
        } catch (Exception e) {
            try {
                return ZonedDateTime.parse(pubDate, DateTimeFormatter.RFC_1123_DATE_TIME).toLocalDateTime();
            } catch (Exception e2) {
                log.warn("[Guardian] Cannot parse pubDate: {}", pubDate);
                return LocalDateTime.now();
            }
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            String resp = httpClient.get(RSS_URL);
            return resp != null && resp.contains("<rss");
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getFrequencyLevel() {
        return "medium";
    }
}
