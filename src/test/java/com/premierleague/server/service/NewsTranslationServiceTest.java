package com.premierleague.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.premierleague.server.entity.News;
import com.premierleague.server.util.HttpClientUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NewsTranslationServiceTest {

    private HttpClientUtil httpClientUtil;
    private NewsTranslationService newsTranslationService;

    @BeforeEach
    void setUp() {
        httpClientUtil = mock(HttpClientUtil.class);
        newsTranslationService = new NewsTranslationService(httpClientUtil, new ObjectMapper());
        ReflectionTestUtils.setField(newsTranslationService, "enabled", true);
        ReflectionTestUtils.setField(newsTranslationService, "provider", "google");
        ReflectionTestUtils.setField(newsTranslationService, "googleBaseUrl", "https://translate.googleapis.com/translate_a/single");
        ReflectionTestUtils.setField(newsTranslationService, "googleMobileUrl", "https://translate.google.com/m");

        when(httpClientUtil.get(any(URI.class))).thenAnswer(invocation -> {
            URI uri = invocation.getArgument(0, URI.class);
            String decoded = decodeQuery(uri.toString());
            String translated = decoded
                    .replace("Haaland faces Arsenal", "哈兰德对阵阿森纳")
                    .replace("Manchester City still have hope.", "曼城仍然保有希望。")
                    .replace("City have nothing to lose.", "曼城已经没有什么可失去的。")
                    .replace("Bayer Leverkusen defender Jarrell Quansah says leaving Liverpool was a \"no-brainer\".",
                            "勒沃库森后卫贾雷尔·夸萨表示离开利物浦是“理所当然的”。");
            return googleResponse(translated);
        });
    }

    @Test
    void translatesEnglishFieldsAndPreservesImageBlocks() {
        News news = News.builder()
                .id("sky-1")
                .title("Haaland faces Arsenal")
                .summary("Manchester City still have hope.")
                .content("Manchester City still have hope.\n\n[IMG:https://img.example.com/a.jpg]\n\nCity have nothing to lose.")
                .source("Sky Sports")
                .sourceType("sky")
                .mediaType("article")
                .sourcePublishedAt(LocalDateTime.of(2026, 4, 20, 12, 0))
                .url("https://www.skysports.com/example")
                .build();

        News translated = newsTranslationService.translateForStorage(news);

        assertEquals("哈兰德对阵阿森纳", translated.getTitle());
        assertEquals("曼城仍然保有希望。", translated.getSummary());
        assertTrue(translated.getContent().contains("曼城仍然保有希望。"));
        assertTrue(translated.getContent().contains("[IMG:https://img.example.com/a.jpg]"));
        assertTrue(translated.getContent().contains("曼城已经没有什么可失去的。"));
    }

    @Test
    void decodesPreviouslyStoredPercentEscapes() {
        News news = News.builder()
                .id("sky-2")
                .title("哈兰德%3A 我对阿森纳没有任何压力")
                .summary("他的球队%22没有什么可失去的%22。")
                .content("他的球队%22没有什么可失去的%22。")
                .source("Sky Sports")
                .sourceType("sky")
                .mediaType("article")
                .sourcePublishedAt(LocalDateTime.of(2026, 4, 20, 12, 0))
                .url("https://www.skysports.com/example")
                .build();

        News translated = newsTranslationService.translateForStorage(news);

        assertEquals("哈兰德: 我对阿森纳没有任何压力", translated.getTitle());
        assertEquals("他的球队\"没有什么可失去的\"。", translated.getSummary());
        assertEquals("他的球队\"没有什么可失去的\"。", translated.getContent());
    }

    @Test
    void detectsPercentEscapedHistoricalDataForCleanup() {
        News news = News.builder()
                .title("英超联赛%3A 周末要点")
                .summary("普通摘要")
                .content("普通正文")
                .build();

        assertTrue(newsTranslationService.needsCleanup(news));
    }

    @Test
    void fallsBackToGoogleMobilePageWhenJsonEndpointReturnsHtml() {
        when(httpClientUtil.get(any(URI.class))).thenAnswer(invocation -> {
            URI uri = invocation.getArgument(0, URI.class);
            String url = uri.toString();
            if (url.contains("/translate_a/single")) {
                return """
                        <HTML><HEAD><TITLE>302 Moved</TITLE></HEAD>
                        <BODY>The document has moved</BODY></HTML>
                        """;
            }
            if (url.contains("translate.google.com/m")) {
                return """
                        <!DOCTYPE html>
                        <html>
                          <body>
                            <div class="result-container">曼城仍有希望。</div>
                          </body>
                        </html>
                        """;
            }
            return null;
        });

        News news = News.builder()
                .id("sky-3")
                .title("Manchester City still have hope.")
                .summary("Manchester City still have hope.")
                .source("Sky Sports")
                .sourceType("sky")
                .mediaType("article")
                .sourcePublishedAt(LocalDateTime.of(2026, 4, 21, 12, 0))
                .url("https://www.skysports.com/example")
                .build();

        News translated = newsTranslationService.translateForStorage(news);

        assertEquals("曼城仍有希望。", translated.getTitle());
        assertEquals("曼城仍有希望。", translated.getSummary());
    }

    @Test
    void normalizesHtmlEntitiesBeforeTranslatingSummary() {
        News news = News.builder()
                .id("sky-4")
                .title("夸萨：离开利物浦加盟勒沃库森是理所当然的事")
                .summary("&#8203;&#8203;&#8203;Bayer Leverkusen&#160;defender&#160;Jarrell Quansah&#160;says leaving Liverpool was a \"no-brainer\".&#160;")
                .content("勒沃库森后卫贾雷尔·夸萨表示离开利物浦是“理所当然的”。")
                .source("Sky Sports")
                .sourceType("sky")
                .mediaType("article")
                .sourcePublishedAt(LocalDateTime.of(2026, 4, 21, 12, 0))
                .url("https://www.skysports.com/example")
                .build();

        News translated = newsTranslationService.translateForStorage(news);

        assertEquals("勒沃库森后卫贾雷尔·夸萨表示离开利物浦是“理所当然的”。", translated.getSummary());
    }

    private String decodeQuery(String url) {
        int qIndex = url.indexOf("&q=");
        String encoded = qIndex >= 0 ? url.substring(qIndex + 3) : "";
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8)
                .replace('\u00A0', ' ')
                .replace("\u200B", "")
                .trim();
    }

    private String googleResponse(String translated) throws JsonProcessingException {
        return new ObjectMapper().writeValueAsString(
                java.util.Arrays.asList(
                        java.util.List.of(
                                java.util.Arrays.asList(translated, "", null, null, 1)
                        ),
                        null,
                        "en"
                )
        );
    }
}
