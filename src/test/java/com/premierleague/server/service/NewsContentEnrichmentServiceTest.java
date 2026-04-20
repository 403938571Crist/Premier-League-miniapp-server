package com.premierleague.server.service;

import com.premierleague.server.entity.News;
import com.premierleague.server.util.HttpClientUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NewsContentEnrichmentServiceTest {

    private HttpClientUtil httpClientUtil;
    private NewsContentEnrichmentService newsContentEnrichmentService;

    @BeforeEach
    void setUp() {
        httpClientUtil = mock(HttpClientUtil.class);
        newsContentEnrichmentService = new NewsContentEnrichmentService(httpClientUtil);
    }

    @Test
    void enrichesSkyArticleContentFromHtmlBody() {
        when(httpClientUtil.getHtml(anyString())).thenReturn("""
                <html><body>
                  <article>
                    <div class="sdc-article-body">
                      <p>Manchester City forward Erling Haaland says he is not feeling the pressure ahead of Arsenal.</p>
                      <p>City come into the crunch game knowing seven wins will almost certainly seal the title.</p>
                      <p>Please use Chrome browser for a more accessible video player</p>
                    </div>
                  </article>
                </body></html>
                """);

        News news = article("sky", "Sky Sports", "https://www.skysports.com/football/news/12040/13533032/example");

        News enriched = newsContentEnrichmentService.enrich(news);

        assertTrue(enriched.getContent().contains("Manchester City forward Erling Haaland says he is not feeling the pressure"));
        assertTrue(enriched.getContent().contains("City come into the crunch game"));
        assertTrue(!enriched.getContent().contains("Please use Chrome browser"));
    }

    @Test
    void enrichesGuardianArticleContentAndSkipsFigureCaptions() {
        when(httpClientUtil.getHtml(anyString())).thenReturn("""
                <html><body>
                  <article>
                    <p>For Manchester City, Gianluigi Donnarumma has always been a case of risk and reward.</p>
                    <figure><p>Caption text that should not be kept.</p></figure>
                    <h2>Donnarumma breathes a sigh of relief</h2>
                    <p>His big mistake did not become the key twist in the title race.</p>
                  </article>
                </body></html>
                """);

        News news = article("guardian", "The Guardian", "https://www.theguardian.com/football/2026/apr/20/example");

        News enriched = newsContentEnrichmentService.enrich(news);

        assertTrue(enriched.getContent().contains("Donnarumma breathes a sigh of relief"));
        assertTrue(enriched.getContent().contains("His big mistake did not become the key twist"));
        assertTrue(!enriched.getContent().contains("Caption text"));
    }

    @Test
    void usesSummaryAsFallbackForOfficialAudioVideoItems() {
        News news = article("official", "英超官方", "https://www.bbc.co.uk/sounds/play/p0n7jvl1");
        news.setSummary("Wayne Rooney reacts to Manchester City v Arsenal and criticises Arsenal fans' support.");

        News enriched = newsContentEnrichmentService.enrich(news);

        assertEquals(news.getSummary(), enriched.getContent());
    }

    @Test
    void treatsSummaryOnlyArticleAsStillNeedingEnrichment() {
        News news = article("sky", "Sky Sports", "https://www.skysports.com/football/news/12040/13533032/example");
        news.setSummary("Short summary");
        news.setContent("Short summary");

        assertTrue(newsContentEnrichmentService.needsEnrichment(news));
    }

    @Test
    void enrichesBbcSportArticleContent() {
        when(httpClientUtil.getHtml(anyString())).thenReturn("""
                <html><body>
                  <article>
                    <div data-component="text-block">
                      <p>Wayne Rooney says Arsenal fans need to be better in supporting the team.</p>
                    </div>
                    <div data-component="text-block">
                      <p>Rooney believes the atmosphere can make the difference in the title race.</p>
                    </div>
                  </article>
                </body></html>
                """);

        News news = article("official", "英超官方", "https://www.bbc.com/sport/football/articles/c5yv3lx1e7ko");

        News enriched = newsContentEnrichmentService.enrich(news);

        assertTrue(enriched.getContent().contains("Wayne Rooney says Arsenal fans need to be better"));
        assertTrue(enriched.getContent().contains("Rooney believes the atmosphere can make the difference"));
    }

    @Test
    void extractsStructuredArticleBodyFromJsonLd() {
        when(httpClientUtil.getHtml(anyString())).thenReturn("""
                <html><head>
                  <script type="application/ld+json">
                    {"@type":"NewsArticle","articleBody":"Manchester City forward Erling Haaland says he has \\\"nothing to lose\\\". <p>City come into the crunch game knowing seven wins will seal the title.</p><p>He says the pressure is on Arsenal.</p>"}
                  </script>
                </head><body></body></html>
                """);

        News news = article("sky", "Sky Sports", "https://www.skysports.com/football/news/12040/13533032/example");

        News enriched = newsContentEnrichmentService.enrich(news);

        assertTrue(enriched.getContent().contains("Manchester City forward Erling Haaland says he has \"nothing to lose\"."));
        assertTrue(enriched.getContent().contains("City come into the crunch game knowing seven wins will seal the title."));
        assertTrue(enriched.getContent().contains("He says the pressure is on Arsenal."));
    }

    @Test
    void prefersStructuredArticleBodyWhenDomOnlyRepeatsSummary() {
        when(httpClientUtil.getHtml(anyString())).thenReturn("""
                <html><head>
                  <script type="application/ld+json">
                    {"@type":"NewsArticle","articleBody":"Manchester City forward Erling Haaland says he is not feeling the pressure ahead of Arsenal. <p>City come into the crunch game knowing seven wins will almost certainly seal the title.</p><p>Pep Guardiola says the pressure remains on Arsenal.</p>"}
                  </script>
                </head><body>
                  <article>
                    <div class="sdc-article-body">
                      <p>Manchester City forward Erling Haaland says he is not feeling the pressure ahead of Arsenal.</p>
                    </div>
                  </article>
                </body></html>
                """);

        News news = article("sky", "Sky Sports", "https://www.skysports.com/football/news/12040/13533032/example");
        news.setSummary("Manchester City forward Erling Haaland says he is not feeling the pressure ahead of Arsenal.");

        News enriched = newsContentEnrichmentService.enrich(news);

        assertTrue(enriched.getContent().contains("City come into the crunch game knowing seven wins will almost certainly seal the title."));
        assertTrue(enriched.getContent().contains("Pep Guardiola says the pressure remains on Arsenal."));
    }

    private News article(String sourceType, String source, String url) {
        return News.builder()
                .id(sourceType + "-1")
                .title("English title")
                .summary("English summary")
                .source(source)
                .sourceType(sourceType)
                .mediaType("article")
                .url(url)
                .build();
    }
}
