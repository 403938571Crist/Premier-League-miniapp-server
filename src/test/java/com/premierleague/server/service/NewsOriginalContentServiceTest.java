package com.premierleague.server.service;

import com.premierleague.server.entity.News;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NewsOriginalContentServiceTest {

    private NewsOriginalContentService newsOriginalContentService;

    @BeforeEach
    void setUp() {
        newsOriginalContentService = new NewsOriginalContentService();
    }

    @Test
    void fillsRomanoContentFromSummary() {
        News news = baseNews("romano");
        news.setSummary("Here we go. Player agrees personal terms with Liverpool.");

        News enriched = newsOriginalContentService.ensureContent(news);

        assertEquals("Here we go. Player agrees personal terms with Liverpool.", enriched.getContent());
        assertFalse(newsOriginalContentService.needsOriginalContent(enriched));
    }

    @Test
    void fillsRedditContentFromReadableSummary() {
        News news = baseNews("reddit");
        news.setSummary("This is a longer Reddit self post discussing Arsenal's title run in detail.");

        News enriched = newsOriginalContentService.ensureContent(news);

        assertEquals("This is a longer Reddit self post discussing Arsenal's title run in detail.", enriched.getContent());
    }

    @Test
    void fallsBackToTitleForRedditMetaOnlySummary() {
        News news = baseNews("reddit");
        news.setTitle("Haaland reaction after Arsenal clash");
        news.setSummary("↑55 · 31 条评论");

        News enriched = newsOriginalContentService.ensureContent(news);

        assertEquals("Haaland reaction after Arsenal clash", enriched.getContent());
    }

    @Test
    void reportsMissingOriginalContentForSupportedSources() {
        News news = baseNews("x");
        news.setSummary("Premier League title race update from a tracked account.");

        assertTrue(newsOriginalContentService.needsOriginalContent(news));
    }

    private News baseNews(String sourceType) {
        return News.builder()
                .id(sourceType + "-1")
                .title("Sample title")
                .summary("")
                .sourceType(sourceType)
                .source(sourceType)
                .mediaType("social")
                .build();
    }
}
