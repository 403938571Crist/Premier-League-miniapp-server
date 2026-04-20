package com.premierleague.server.service;

import com.premierleague.server.entity.News;
import com.premierleague.server.util.HttpClientUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsContentEnrichmentService {

    private static final Set<String> CONTENT_SOURCES = Set.of("sky", "guardian", "official");
    private static final int MIN_TEXT_BLOCK_LENGTH = 35;
    private static final int MIN_USABLE_CONTENT_LENGTH = 120;

    private final HttpClientUtil httpClientUtil;

    public News enrich(News news) {
        if (news == null || news.getSourceType() == null) {
            return news;
        }

        String sourceType = news.getSourceType().toLowerCase(Locale.ROOT);
        if (!CONTENT_SOURCES.contains(sourceType) || !needsEnrichment(news)) {
            return news;
        }

        if ("official".equals(sourceType) && isBbcMediaPage(news.getUrl())) {
            fillContentFromSummary(news);
            return news;
        }

        String html = httpClientUtil.getHtml(news.getUrl());
        if (html == null || html.isBlank()) {
            log.debug("[NewsContent] Empty HTML for {} {}", sourceType, news.getUrl());
            fillContentFromSummary(news);
            return news;
        }

        try {
            Document document = Jsoup.parse(html, news.getUrl());
            String content = switch (sourceType) {
                case "sky" -> extractSkyContent(document);
                case "guardian" -> extractGuardianContent(document);
                case "official" -> extractBbcContent(document);
                default -> null;
            };
            if (shouldFallbackToStructured(news, content)) {
                log.debug("[NewsContent] DOM content unusable for {} {}, trying structured body", sourceType, news.getUrl());
                content = extractStructuredArticleBody(html);
            }

            if (hasUsableExtractedContent(news, content)) {
                log.debug("[NewsContent] Enriched {} {} with {} chars", sourceType, news.getUrl(), content.length());
                news.setContent(content);
            } else {
                log.debug("[NewsContent] Fallback to summary for {} {}", sourceType, news.getUrl());
                fillContentFromSummary(news);
            }

            if (!hasContent(news.getCoverImage())) {
                document.select("meta[property=og:image], meta[name=og:image]").stream()
                        .map(element -> element.attr("content"))
                        .filter(this::hasContent)
                        .findFirst()
                        .ifPresent(news::setCoverImage);
            }
        } catch (Exception e) {
            log.warn("[NewsContent] Failed to enrich {}", news.getUrl(), e);
            fillContentFromSummary(news);
        }

        return news;
    }

    public boolean needsEnrichment(News news) {
        if (news == null || news.getSourceType() == null) {
            return false;
        }
        String sourceType = news.getSourceType().toLowerCase(Locale.ROOT);
        if (!CONTENT_SOURCES.contains(sourceType)) {
            return false;
        }
        if ("official".equals(sourceType) && isBbcMediaPage(news.getUrl())) {
            return !hasContent(news.getContent());
        }
        return !hasUsableContent(news);
    }

    private String extractSkyContent(Document document) {
        return extractFromSelectors(document, List.of(
                "div.sdc-article-body",
                "[data-component-name=article-body]",
                "main article",
                "article"
        ));
    }

    private String extractGuardianContent(Document document) {
        return extractFromSelectors(document, List.of(
                "[data-gu-name=body]",
                "main article",
                "article"
        ));
    }

    private String extractBbcContent(Document document) {
        return extractFromSelectors(document, List.of(
                "article [data-component=text-block]",
                "main [data-component=text-block]",
                "main article",
                "article"
        ));
    }

    private String extractFromSelectors(Document document, List<String> selectors) {
        for (String selector : selectors) {
            Elements containers = document.select(selector);
            if (containers.isEmpty()) {
                continue;
            }
            String extracted = buildContent(containers);
            if (hasContent(extracted)) {
                return extracted;
            }
        }
        return null;
    }

    private String buildContent(Elements containers) {
        LinkedHashSet<String> blocks = new LinkedHashSet<>();
        for (Element container : containers) {
            for (Element element : container.select("h2, h3, p, img")) {
                if (shouldSkip(element)) {
                    continue;
                }

                if ("img".equals(element.tagName())) {
                    String imageUrl = resolveImageUrl(element);
                    if (hasContent(imageUrl)) {
                        blocks.add("[IMG:" + imageUrl + "]");
                    }
                    continue;
                }

                String text = normalizeText(element.text());
                if (shouldKeepText(element.tagName(), text)) {
                    blocks.add(text);
                }
            }
        }
        return String.join("\n\n", new ArrayList<>(blocks));
    }

    private boolean shouldSkip(Element element) {
        if (element == null) {
            return true;
        }
        for (Element parent : element.parents()) {
            String tag = parent.tagName();
            if ("figure".equals(tag) || "aside".equals(tag) || "nav".equals(tag) || "footer".equals(tag)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldKeepText(String tagName, String text) {
        if (!hasContent(text)) {
            return false;
        }

        String normalized = text.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("please use chrome browser")
                || normalized.startsWith("watch ")
                || normalized.startsWith("get sky")
                || normalized.startsWith("not got sky")
                || normalized.startsWith("share")
                || normalized.startsWith("read more")
                || normalized.startsWith("listen ")
                || normalized.contains("accessible video player")) {
            return false;
        }

        if ("h2".equals(tagName) || "h3".equals(tagName)) {
            return text.length() >= 8;
        }
        return text.length() >= MIN_TEXT_BLOCK_LENGTH;
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String resolveImageUrl(Element image) {
        for (String attr : List.of("src", "data-src", "data-original", "data-original-mos")) {
            String value = image.attr(attr).trim();
            if (hasContent(value) && value.startsWith("http")) {
                return value;
            }
        }
        return null;
    }

    private void fillContentFromSummary(News news) {
        if (!hasContent(news.getContent()) && hasContent(news.getSummary())) {
            news.setContent(news.getSummary());
        }
    }

    private boolean shouldFallbackToStructured(News news, String candidate) {
        return !hasUsableExtractedContent(news, candidate);
    }

    private String extractStructuredArticleBody(String html) {
        if (!hasContent(html)) {
            return null;
        }

        String raw = extractJsonStringField(html, "\"articleBody\"");
        if (!hasContent(raw)) {
            return null;
        }

        String decoded = raw
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\/", "/")
                .replace("\\\\", "\\");
        decoded = decoded
                .replaceAll("(?i)</p>\\s*<p>", "\n\n")
                .replaceAll("(?i)</li>\\s*<li>", "\n\n")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</?(p|ul|ol|li|strong|em|a)[^>]*>", "");
        decoded = Parser.unescapeEntities(decoded, false).trim();
        log.debug("[NewsContent] Structured articleBody extracted {} chars", decoded.length());
        return decoded.length() >= MIN_USABLE_CONTENT_LENGTH ? decoded : null;
    }

    private String extractJsonStringField(String html, String fieldName) {
        int fieldIndex = html.indexOf(fieldName);
        if (fieldIndex < 0) {
            return null;
        }

        int colonIndex = html.indexOf(':', fieldIndex + fieldName.length());
        if (colonIndex < 0) {
            return null;
        }

        int valueStart = colonIndex + 1;
        while (valueStart < html.length() && Character.isWhitespace(html.charAt(valueStart))) {
            valueStart++;
        }
        if (valueStart >= html.length() || html.charAt(valueStart) != '"') {
            return null;
        }

        StringBuilder raw = new StringBuilder();
        boolean escaping = false;
        for (int i = valueStart + 1; i < html.length(); i++) {
            char current = html.charAt(i);
            if (escaping) {
                raw.append('\\').append(current);
                escaping = false;
                continue;
            }
            if (current == '\\') {
                escaping = true;
                continue;
            }
            if (current == '"') {
                return raw.toString();
            }
            raw.append(current);
        }

        return null;
    }

    private boolean isBbcMediaPage(String url) {
        if (!hasContent(url)) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("/videos/") || lower.contains("/sounds/play/");
    }

    private boolean hasContent(String value) {
        return value != null && !value.isBlank();
    }

    private boolean hasUsableContent(News news) {
        if (!hasContent(news.getContent())) {
            return false;
        }
        if ("official".equalsIgnoreCase(news.getSourceType()) && isBbcMediaPage(news.getUrl())) {
            return true;
        }
        return hasUsableExtractedContent(news, news.getContent());
    }

    private boolean hasUsableExtractedContent(News news, String candidate) {
        if (!hasContent(candidate)) {
            return false;
        }

        String normalizedCandidate = normalizeComparable(candidate);
        String normalizedSummary = normalizeComparable(news.getSummary());
        if (!normalizedSummary.isEmpty() && normalizedCandidate.equals(normalizedSummary)) {
            return false;
        }
        return candidate.length() >= MIN_USABLE_CONTENT_LENGTH || candidate.contains("\n\n");
    }

    private String normalizeComparable(String text) {
        return hasContent(text) ? text.replaceAll("\\s+", " ").trim() : "";
    }
}
