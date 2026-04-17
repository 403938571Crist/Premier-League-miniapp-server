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
 * Sky Sports 英超资讯 - 中频源
 * RSS: https://www.skysports.com/rss/12040
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkyProvider implements NewsProvider {

    private final HttpClientUtil httpClient;
    private final ContentCleanService contentCleanService;

    private static final String RSS_URL = "https://www.skysports.com/rss/12040";
    private static final DateTimeFormatter RFC1123 =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);

    @Override
    public String getSourceType() {
        return "sky";
    }

    @Override
    public String getSourceName() {
        return "Sky Sports";
    }

    @Override
    public List<News> fetchLatest(int maxItems) {
        log.info("[Sky] Fetching from RSS: {}", RSS_URL);
        try {
            String xml = httpClient.get(RSS_URL);
            if (xml == null || xml.isBlank()) {
                log.warn("[Sky] Empty RSS response");
                return Collections.emptyList();
            }
            return parseRss(xml, maxItems);
        } catch (Exception e) {
            log.error("[Sky] Failed to fetch RSS", e);
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
            log.info("[Sky] Parsed {} items", newsList.size());
        } catch (Exception e) {
            log.error("[Sky] Failed to parse RSS", e);
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

            if (title.isEmpty() || link.isEmpty()) {
                return null;
            }

            // Sky Sports PL RSS 已经只含英超内容，但仍做二次过滤
            if (!isPremierLeagueRelated(title + " " + description)) {
                log.debug("[Sky] Skipping non-PL: {}", title.substring(0, Math.min(50, title.length())));
                return null;
            }

            // 提取封面图：Sky RSS 中 <media:content url="..."/> 或 <enclosure url="..."/>
            String coverImage = extractMediaUrl(item);

            LocalDateTime publishTime = parsePubDate(pubDate);
            String idBase = guid.isEmpty() ? link : guid;

            News news = News.builder()
                    .id("sky-" + Math.abs(idBase.hashCode()))
                    .title(title)
                    .summary(description.isEmpty() ? title : description.substring(0, Math.min(description.length(), 2000)))
                    .source("Sky Sports")
                    .sourceType("sky")
                    .mediaType("article")
                    .sourcePublishedAt(publishTime)
                    .url(link)
                    .coverImage(coverImage)
                    .author("Sky Sports")
                    .build();

            news.setTags(contentCleanService.extractTags(title, description));
            news.setHotScore(contentCleanService.calculateDefaultHotScore(news));
            return news;
        } catch (Exception e) {
            log.error("[Sky] Failed to parse item", e);
            return null;
        }
    }

    private String extractMediaUrl(Element item) {
        // <media:content url="..."/>
        NodeList mediaNodes = item.getElementsByTagNameNS("http://search.yahoo.com/mrss/", "content");
        if (mediaNodes.getLength() > 0) {
            String url = ((Element) mediaNodes.item(0)).getAttribute("url");
            if (!url.isEmpty()) return url;
        }
        // <enclosure url="..." type="image/..."/>
        NodeList encNodes = item.getElementsByTagName("enclosure");
        if (encNodes.getLength() > 0) {
            Element enc = (Element) encNodes.item(0);
            String type = enc.getAttribute("type");
            if (type.startsWith("image")) {
                return enc.getAttribute("url");
            }
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
                log.warn("[Sky] Cannot parse pubDate: {}", pubDate);
                return LocalDateTime.now();
            }
        }
    }

    private boolean isPremierLeagueRelated(String text) {
        String lower = text.toLowerCase();

        // 先排除明确的非足球项目（飞镖、F1、板球等也叫"Premier League"）
        if (lower.contains("formula 1") || lower.contains(" f1 ") || lower.contains("grand prix")
                || lower.contains("darts") || lower.contains("cricket") || lower.contains("rugby")
                || lower.contains("nfl") || lower.contains("nba") || lower.contains("golf")) {
            return false;
        }

        // 匹配英超球队或英超直接关键词（不再用 transfer/signing 等泛词）
        String[] keywords = {
            "premier league", "premierleague", "epl",
            "arsenal", "liverpool", "manchester city", "man city",
            "manchester united", "man utd", "chelsea", "tottenham", "spurs",
            "newcastle", "brighton", "aston villa", "west ham",
            "brentford", "crystal palace", "everton", "fulham",
            "nottingham forest", "wolves", "wolverhampton",
            "bournemouth", "ipswich", "leicester", "southampton"
        };
        for (String kw : keywords) {
            if (lower.contains(kw)) return true;
        }
        return false;
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
