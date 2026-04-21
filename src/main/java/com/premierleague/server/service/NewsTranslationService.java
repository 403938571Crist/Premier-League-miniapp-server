package com.premierleague.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.premierleague.server.entity.News;
import com.premierleague.server.util.HttpClientUtil;
import org.jsoup.Jsoup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.jsoup.parser.Parser;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsTranslationService {

    private static final Pattern LATIN_WORD_PATTERN = Pattern.compile("\\b[A-Za-z][A-Za-z'’-]{2,}\\b");
    private static final Pattern CJK_PATTERN = Pattern.compile("\\p{IsHan}");
    private static final Pattern BLOCK_MARKER_PATTERN = Pattern.compile("<<<PL_BLOCK_(\\d+)>>>");
    private static final Pattern URL_ESCAPE_PATTERN = Pattern.compile("%[0-9A-Fa-f]{2}");
    private static final int MAX_TRANSLATE_BATCH_CHARS = 1200;

    private final HttpClientUtil httpClientUtil;
    private final ObjectMapper objectMapper;

    @Value("${app.translation.enabled:true}")
    private boolean enabled;

    @Value("${app.translation.provider:google}")
    private String provider;

    @Value("${app.translation.google.base-url:https://translate.googleapis.com/translate_a/single}")
    private String googleBaseUrl;

    @Value("${app.translation.google.mobile-url:https://translate.google.com/m}")
    private String googleMobileUrl;

    public News translateForStorage(News news) {
        if (news == null || !enabled) {
            return news;
        }

        news.setTitle(normalizeStoredText(news.getTitle()));
        news.setSummary(normalizeStoredText(news.getSummary()));
        news.setContent(normalizeStoredText(news.getContent()));

        if (needsTranslation(news.getTitle())) {
            news.setTitle(translateText(news.getTitle()));
        }
        if (needsTranslation(news.getSummary())) {
            news.setSummary(translateText(news.getSummary()));
        }
        if (needsTranslation(news.getContent())) {
            news.setContent(translateContent(news.getContent()));
        }
        return news;
    }

    public boolean needsTranslation(News news) {
        if (news == null) {
            return false;
        }
        return needsTranslation(news.getTitle())
                || needsTranslation(news.getSummary())
                || needsTranslation(news.getContent());
    }

    public boolean needsCleanup(News news) {
        if (news == null) {
            return false;
        }
        return needsCleanup(news.getTitle())
                || needsCleanup(news.getSummary())
                || needsCleanup(news.getContent());
    }

    public boolean needsTranslation(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        int latinWords = countMatches(LATIN_WORD_PATTERN, text);
        int cjkChars = countMatches(CJK_PATTERN, text);
        return latinWords >= 3 && latinWords * 2 >= Math.max(1, cjkChars);
    }

    public boolean needsCleanup(String text) {
        return text != null && URL_ESCAPE_PATTERN.matcher(text).find();
    }

    private int countMatches(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private String translateContent(String content) {
        String[] blocks = content.split("\n\n");
        List<Integer> textIndexes = new ArrayList<>();
        StringBuilder batch = new StringBuilder();
        Map<Integer, String> translatedBlocks = new LinkedHashMap<>();
        for (int i = 0; i < blocks.length; i++) {
            if (isImageBlock(blocks[i]) || !needsTranslation(blocks[i])) {
                continue;
            }
            String segment = "<<<PL_BLOCK_" + i + ">>>\n" + blocks[i].trim();
            if (batch.length() > 0 && batch.length() + 1 + segment.length() > MAX_TRANSLATE_BATCH_CHARS) {
                translatedBlocks.putAll(translateBatch(batch.toString(), textIndexes, blocks));
                textIndexes.clear();
                batch.setLength(0);
            }
            if (batch.length() > 0) {
                batch.append('\n');
            }
            batch.append(segment);
            textIndexes.add(i);
        }

        if (!textIndexes.isEmpty()) {
            translatedBlocks.putAll(translateBatch(batch.toString(), textIndexes, blocks));
        }

        if (translatedBlocks.isEmpty()) {
            return content;
        }

        for (Map.Entry<Integer, String> entry : translatedBlocks.entrySet()) {
            String translated = entry.getValue();
            if (translated != null && !translated.isBlank()) {
                blocks[entry.getKey()] = translated.trim();
            }
        }
        return String.join("\n\n", blocks);
    }

    private String normalizeStoredText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String normalized = text;
        if (URL_ESCAPE_PATTERN.matcher(normalized).find()) {
            try {
                normalized = URLDecoder.decode(normalized, StandardCharsets.UTF_8);
            } catch (Exception e) {
                return text;
            }
        }

        try {
            normalized = Parser.unescapeEntities(normalized, false)
                    .replace('\u00A0', ' ')
                    .replace("\u200B", "")
                    .trim();
            return normalized;
        } catch (Exception e) {
            return text;
        }
    }

    private Map<Integer, String> translateBatch(String batchText, List<Integer> indexes, String[] originalBlocks) {
        Map<Integer, String> translatedBlocks = parseTranslatedBlocks(translateText(batchText));
        if (translatedBlocks.size() == indexes.size()) {
            return translatedBlocks;
        }

        translatedBlocks = new LinkedHashMap<>();
        for (Integer index : indexes) {
            translatedBlocks.put(index, translateText(originalBlocks[index]));
        }
        return translatedBlocks;
    }

    private Map<Integer, String> parseTranslatedBlocks(String translatedBatch) {
        Map<Integer, String> translatedBlocks = new LinkedHashMap<>();
        if (translatedBatch == null || translatedBatch.isBlank()) {
            return translatedBlocks;
        }

        Matcher matcher = BLOCK_MARKER_PATTERN.matcher(translatedBatch);
        Integer currentIndex = null;
        int contentStart = -1;
        while (matcher.find()) {
            if (currentIndex != null && contentStart >= 0) {
                translatedBlocks.put(currentIndex, translatedBatch.substring(contentStart, matcher.start()).trim());
            }
            currentIndex = Integer.parseInt(matcher.group(1));
            contentStart = matcher.end();
        }
        if (currentIndex != null && contentStart >= 0) {
            translatedBlocks.put(currentIndex, translatedBatch.substring(contentStart).trim());
        }
        return translatedBlocks;
    }

    private boolean isImageBlock(String block) {
        return block != null && block.startsWith("[IMG:") && block.endsWith("]");
    }

    private String translateText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        if (!"google".equalsIgnoreCase(provider)) {
            return text;
        }

        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(googleBaseUrl)
                    .queryParam("client", "gtx")
                    .queryParam("sl", "auto")
                    .queryParam("tl", "zh-CN")
                    .queryParam("dt", "t")
                    .queryParam("q", text)
                    .build()
                    .toUri();
            String response = httpClientUtil.get(uri);
            if (response == null || response.isBlank()) {
                return translateWithGoogleMobilePage(text);
            }
            if (looksLikeHtml(response)) {
                return translateWithGoogleMobilePage(text);
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode segments = root.path(0);
            if (!segments.isArray()) {
                return translateWithGoogleMobilePage(text);
            }

            StringBuilder translated = new StringBuilder();
            for (JsonNode segment : segments) {
                if (segment.isArray() && segment.size() > 0) {
                    translated.append(segment.get(0).asText());
                }
            }

            String output = translated.toString().trim();
            return output.isEmpty() ? translateWithGoogleMobilePage(text) : output;
        } catch (Exception e) {
            log.warn("[NewsTranslation] Failed to translate text", e);
            return translateWithGoogleMobilePage(text);
        }
    }

    private String translateWithGoogleMobilePage(String text) {
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(googleMobileUrl)
                    .queryParam("sl", "auto")
                    .queryParam("tl", "zh-CN")
                    .queryParam("q", text)
                    .build()
                    .toUri();
            String html = httpClientUtil.get(uri);
            if (html == null || html.isBlank()) {
                return text;
            }

            String translated = Jsoup.parse(html)
                    .selectFirst(".result-container")
                    .text()
                    .trim();
            return translated.isEmpty() ? text : translated;
        } catch (Exception e) {
            log.warn("[NewsTranslation] Google mobile fallback failed", e);
            return text;
        }
    }

    private boolean looksLikeHtml(String response) {
        String value = response == null ? "" : response.trim().toLowerCase();
        return value.startsWith("<!doctype html")
                || value.startsWith("<html")
                || value.startsWith("<head")
                || value.startsWith("<body")
                || value.startsWith("<title");
    }
}
