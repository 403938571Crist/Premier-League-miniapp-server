package com.premierleague.server.provider;

import com.premierleague.server.util.HttpClientUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WikipediaPlayerPhotoProviderTest {

    private HttpClientUtil httpClientUtil;
    private WikipediaPlayerPhotoProvider provider;

    @BeforeEach
    void setUp() {
        httpClientUtil = mock(HttpClientUtil.class);
        provider = new WikipediaPlayerPhotoProvider(httpClientUtil);
    }

    @Test
    void findPhotoUrlReturnsSummaryThumbnailWhenAvailable() {
        when(httpClientUtil.get(contains("/api/rest_v1/page/summary/Tommy_Setford")))
                .thenReturn("""
                        {
                          "thumbnail": {
                            "source": "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c6/Setford_%28cropped%29.jpg/330px-Setford_%28cropped%29.jpg"
                          }
                        }
                        """);

        String photoUrl = provider.findPhotoUrl("Tommy Setford");

        assertEquals(
                "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c6/Setford_%28cropped%29.jpg/330px-Setford_%28cropped%29.jpg",
                photoUrl
        );
    }

    @Test
    void findPhotoUrlFallsBackToCommonsCategoryImageWhenSummaryHasNoImage() {
        when(httpClientUtil.get(contains("/api/rest_v1/page/summary/Ryan_Kavuma-McQueen")))
                .thenReturn("""
                        {
                          "title": "Ryan Kavuma-McQueen"
                        }
                        """);
        when(httpClientUtil.get(contains("wbsearchentities&search=Ryan+Kavuma-McQueen")))
                .thenReturn("""
                        {
                          "search": [
                            {
                              "id":"Q136678018",
                              "label":"Ryan Kavuma-McQueen",
                              "description":"English footballer (born 2009)"
                            }
                          ]
                        }
                        """);
        when(httpClientUtil.get(contains("Special:EntityData/Q136678018.json")))
                .thenReturn("""
                        {
                          "entities": {
                            "Q136678018": {
                              "sitelinks": {
                                "commonswiki": {
                                  "title": "Category:Ryan Kavuma-McQueen"
                                }
                              }
                            }
                          }
                        }
                        """);
        when(httpClientUtil.get(contains("commons.wikimedia.org/w/api.php?action=query&list=categorymembers")))
                .thenReturn("""
                        {
                          "query": {
                            "categorymembers": [
                              {
                                "title": "File:Ryan_Kavuma-McQueen_2026.jpg"
                              }
                            ]
                          }
                        }
                        """);
        when(httpClientUtil.get(contains("commons.wikimedia.org/w/api.php?action=query&titles=File%3ARyan_Kavuma-McQueen_2026.jpg")))
                .thenReturn("""
                        {
                          "query": {
                            "pages": {
                              "1": {
                                "imageinfo": [
                                  {
                                    "url": "https://upload.wikimedia.org/wikipedia/commons/9/99/Ryan_Kavuma-McQueen_2026.jpg"
                                  }
                                ]
                              }
                            }
                          }
                        }
                        """);

        String photoUrl = provider.findPhotoUrl("Ryan Kavuma-McQueen");

        assertEquals("https://upload.wikimedia.org/wikipedia/commons/9/99/Ryan_Kavuma-McQueen_2026.jpg", photoUrl);
    }

    @Test
    void findPhotoUrlReturnsNullWhenNoWikipediaOrCommonsImageExists() {
        when(httpClientUtil.get(contains("/api/rest_v1/page/summary/Unknown_Prospect")))
                .thenReturn("""
                        {
                          "title": "Unknown Prospect"
                        }
                        """);
        when(httpClientUtil.get(contains("wbsearchentities&search=Unknown+Prospect")))
                .thenReturn("""
                        {
                          "search": []
                        }
                        """);

        assertNull(provider.findPhotoUrl("Unknown Prospect"));
    }
}
