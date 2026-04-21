package com.premierleague.server.provider;

import com.premierleague.server.model.PlayerSocialCandidate;
import com.premierleague.server.util.HttpClientUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WikidataPlayerSocialProviderTest {

    private HttpClientUtil httpClientUtil;
    private WikidataPlayerSocialProvider provider;

    @BeforeEach
    void setUp() {
        httpClientUtil = mock(HttpClientUtil.class);
        provider = new WikidataPlayerSocialProvider(httpClientUtil);
    }

    @Test
    void fetchProfilesBuildsPlatformSpecificHandlesFromWikidataClaims() {
        when(httpClientUtil.get(contains("wbsearchentities&search=Mohamed+Salah")))
                .thenReturn("""
                        {
                          "search": [
                            {
                              "id":"Q1354960",
                              "label":"Mohamed Salah",
                              "description":"Egyptian association football player (born 1992)"
                            }
                          ]
                        }
                        """);
        when(httpClientUtil.get(contains("Special:EntityData/Q1354960.json")))
                .thenReturn("""
                        {
                          "entities": {
                            "Q1354960": {
                              "claims": {
                                "P2002": [{"mainsnak":{"datavalue":{"value":"MoSalah"}}}],
                                "P2003": [{"mainsnak":{"datavalue":{"value":"mosalah"}}}],
                                "P2013": [{"mainsnak":{"datavalue":{"value":"MomoSalah"}}}]
                              }
                            }
                          }
                        }
                        """);

        List<PlayerSocialCandidate> profiles = provider.fetchProfiles("Mohamed Salah");

        assertEquals(3, profiles.size());
        assertEquals("X", profiles.get(0).platform());
        assertEquals("@MoSalah", profiles.get(0).handle());
        assertEquals("https://x.com/MoSalah", profiles.get(0).profileUrl());
        assertEquals("Instagram", profiles.get(1).platform());
        assertEquals("@mosalah", profiles.get(1).handle());
        assertEquals("Facebook", profiles.get(2).platform());
        assertEquals("https://www.facebook.com/MomoSalah", profiles.get(2).profileUrl());
    }

    @Test
    void fetchProfilesIgnoresNonFootballMatches() {
        when(httpClientUtil.get(contains("wbsearchentities&search=John+Smith")))
                .thenReturn("""
                        {
                          "search": [
                            {
                              "id":"Q1",
                              "label":"John Smith",
                              "description":"chemist"
                            }
                          ]
                        }
                        """);

        List<PlayerSocialCandidate> profiles = provider.fetchProfiles("John Smith");

        assertTrue(profiles.isEmpty());
    }
}
