package com.premierleague.server.provider;

import com.premierleague.server.util.HttpClientUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlPhotoProviderTest {

    private HttpClientUtil http;
    private WikipediaPlayerPhotoProvider wikipediaPlayerPhotoProvider;
    private PlPhotoProvider provider;

    @BeforeEach
    void setUp() {
        http = mock(HttpClientUtil.class);
        wikipediaPlayerPhotoProvider = mock(WikipediaPlayerPhotoProvider.class);
        provider = new PlPhotoProvider(http, wikipediaPlayerPhotoProvider);
    }

    @Test
    void keepsLegacy250PhotoWhenAvailable() {
        stubSearch("Eli Junior Kroupi", "p560262");
        String legacyUrl = "https://resources.premierleague.com/premierleague/photos/players/250x250/p560262.png";
        String currentUrl = "https://resources.premierleague.com/premierleague25/photos/players/110x140/560262.png";
        when(http.headOk(legacyUrl, imageHeaders())).thenReturn(true);

        assertEquals(legacyUrl, provider.findPhotoUrl("Eli Junior Kroupi"));
        verify(http, never()).headOk(currentUrl, imageHeaders());
    }

    @Test
    void fallsBackToCurrentSeasonPhotoWhenLegacy250PhotoIsMissing() {
        stubSearch("Eli Junior Kroupi", "p560262");
        String legacyUrl = "https://resources.premierleague.com/premierleague/photos/players/250x250/p560262.png";
        String currentUrl = "https://resources.premierleague.com/premierleague25/photos/players/110x140/560262.png";
        when(http.headOk(legacyUrl, imageHeaders())).thenReturn(false);
        when(http.headOk(currentUrl, imageHeaders())).thenReturn(true);

        assertEquals(currentUrl, provider.findPhotoUrl("Eli Junior Kroupi"));
    }

    @Test
    void replacesBrokenLegacyCandidateWithoutSearchingAgain() {
        String legacyUrl = "https://resources.premierleague.com/premierleague/photos/players/250x250/p560262.png";
        String currentUrl = "https://resources.premierleague.com/premierleague25/photos/players/110x140/560262.png";
        when(http.headOk(legacyUrl, imageHeaders())).thenReturn(false);
        when(http.headOk(currentUrl, imageHeaders())).thenReturn(true);

        assertEquals(currentUrl, provider.findUsablePhotoUrl("Eli Junior Kroupi", legacyUrl));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
    }

    @Test
    void fallsBackToWikipediaPhotoWhenOfficialAssetDoesNotExist() {
        stubSearch("Ryan Kavuma-McQueen", "p616286");
        String legacyUrl = "https://resources.premierleague.com/premierleague/photos/players/250x250/p616286.png";
        String currentUrl = "https://resources.premierleague.com/premierleague25/photos/players/110x140/616286.png";
        String smallUrl = "https://resources.premierleague.com/premierleague25/photos/players/40x40/616286.png";
        when(http.headOk(legacyUrl, imageHeaders())).thenReturn(false);
        when(http.headOk(currentUrl, imageHeaders())).thenReturn(false);
        when(http.headOk(smallUrl, imageHeaders())).thenReturn(false);
        when(wikipediaPlayerPhotoProvider.findPhotoUrl("Ryan Kavuma-McQueen"))
                .thenReturn("https://upload.wikimedia.org/wikipedia/commons/9/99/Ryan_Kavuma-McQueen_2026.jpg");

        assertEquals(
                "https://upload.wikimedia.org/wikipedia/commons/9/99/Ryan_Kavuma-McQueen_2026.jpg",
                provider.findPhotoUrl("Ryan Kavuma-McQueen")
        );
    }

    @Test
    void resolvesKnownOptaOverrideForChidoObiMartin() {
        String legacyUrl = "https://resources.premierleague.com/premierleague/photos/players/250x250/p596047.png";
        String currentUrl = "https://resources.premierleague.com/premierleague25/photos/players/110x140/596047.png";
        when(http.headOk(legacyUrl, imageHeaders())).thenReturn(false);
        when(http.headOk(currentUrl, imageHeaders())).thenReturn(true);

        assertEquals(currentUrl, provider.findPhotoUrl("Chido Obi-Martin"));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
    }

    @Test
    void resolvesKnownOptaOverrideForIfeoluwaIbrahim() {
        String legacyUrl = "https://resources.premierleague.com/premierleague/photos/players/250x250/p616068.png";
        String currentUrl = "https://resources.premierleague.com/premierleague25/photos/players/110x140/616068.png";
        when(http.headOk(legacyUrl, imageHeaders())).thenReturn(false);
        when(http.headOk(currentUrl, imageHeaders())).thenReturn(true);

        assertEquals(currentUrl, provider.findPhotoUrl("Ifeoluwa Ibrahim"));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
    }

    @Test
    void resolvesKnownOptaOverrideForCurlyApostropheName() {
        String legacyUrl = "https://resources.premierleague.com/premierleague/photos/players/250x250/p645551.png";
        String currentUrl = "https://resources.premierleague.com/premierleague25/photos/players/110x140/645551.png";
        when(http.headOk(legacyUrl, imageHeaders())).thenReturn(false);
        when(http.headOk(currentUrl, imageHeaders())).thenReturn(true);

        assertEquals(currentUrl, provider.findPhotoUrl("Ceadach O’Neill"));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
    }

    private void stubSearch(String displayName, String opta) {
        String body = """
                {"hits":{"hit":[{"response":{"altIds":{"opta":"%s"},"name":{"display":"%s"}}}]}}
                """.formatted(opta, displayName);
        when(http.getWithHeaders(anyString(), anyMap())).thenReturn(body);
    }

    private Map<String, String> imageHeaders() {
        return Map.of(
                "Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
                "Referer", "https://www.premierleague.com/",
                "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        );
    }
}
