package com.premierleague.server.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.premierleague.server.util.HttpClientUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlPhotoProvider {

    private static final String SEARCH_URL =
            "https://footballapi.pulselive.com/search/PremierLeague"
                    + "?terms=%s&type=player&size=3&start=0&fullObjectResponse=true";

    private static final String LEGACY_PHOTO_URL_TMPL =
            "https://resources.premierleague.com/premierleague/photos/players/250x250/%s.png";

    private static final String CURRENT_PHOTO_URL_TMPL =
            "https://resources.premierleague.com/premierleague25/photos/players/110x140/%s.png";

    private static final String CURRENT_SMALL_PHOTO_URL_TMPL =
            "https://resources.premierleague.com/premierleague25/photos/players/40x40/%s.png";

    private static final Map<String, String> IMAGE_HEADERS = Map.of(
            "Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
            "Referer", "https://www.premierleague.com/",
            "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    );

    private static final Map<String, String> SEARCH_ALIASES = Map.ofEntries(
            Map.entry("Gabriel", "Gabriel Magalhaes"),
            Map.entry("Gabriel Magalhães", "Gabriel Magalhaes"),
            Map.entry("Sávio", "Savinho"),
            Map.entry("Savio", "Savinho"),
            Map.entry("Nico O’Reilly", "Nico O'Reilly"),
            Map.entry("Nico OReilly", "Nico O'Reilly"),
            Map.entry("Chido Obi-Martin", "Chido Obi"),
            Map.entry("Ifeoluwa Ibrahim", "Ife Ibrahim")
    );

    private static final Map<String, String> OPTA_OVERRIDES = Map.ofEntries(
            Map.entry("Gabriel", "p226597"),
            Map.entry("Sávio", "p510281"),
            Map.entry("Savio", "p510281"),
            Map.entry("Nico O'Reilly", "p472769"),
            Map.entry("Nico O’Reilly", "p472769"),
            Map.entry("Chido Obi", "p596047"),
            Map.entry("Chido Obi-Martin", "p596047"),
            Map.entry("Ife Ibrahim", "p616068"),
            Map.entry("Ifeoluwa Ibrahim", "p616068"),
            Map.entry("Ceadach O'Neill", "p645551"),
            Map.entry("Ceadach O’Neill", "p645551")
    );

    private final HttpClientUtil http;
    private final WikipediaPlayerPhotoProvider wikipediaPlayerPhotoProvider;
    private final ObjectMapper mapper = new ObjectMapper();

    // Cache positive hits only so transient network failures do not get stuck as permanent misses.
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String findPhotoUrl(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return null;
        }

        String key = playerName.trim();
        String hit = cache.get(key);
        if (hit != null) {
            return hit;
        }

        String overridePhoto = resolveOverridePhoto(key);
        if (overridePhoto != null) {
            cache.put(key, overridePhoto);
            return overridePhoto;
        }

        String photo = searchByAlias(key);
        if (photo == null) {
            photo = searchByName(key);
        }
        if (photo == null) {
            String normalized = stripAccents(key);
            if (!normalized.equals(key)) {
                photo = searchByName(normalized);
            }
        }

        if (photo == null) {
            String[] parts = key.split("\\s+");
            if (parts.length > 1) {
                String lastName = stripAccents(parts[parts.length - 1]);
                photo = searchByName(lastName);
            }
        }

        if (photo == null) {
            photo = wikipediaPlayerPhotoProvider.findPhotoUrl(key);
        }

        if (photo != null) {
            cache.put(key, photo);
        }
        return photo;
    }

    public String findUsablePhotoUrl(String playerName, String candidateUrl) {
        if (candidateUrl != null && !candidateUrl.isBlank()) {
            String url = candidateUrl.trim();
            if (http.headOk(url, IMAGE_HEADERS)) {
                return url;
            }

            String opta = extractOptaFromPhotoUrl(url);
            if (opta != null) {
                String resolved = resolvePhotoUrl(opta);
                if (resolved != null) {
                    return resolved;
                }
            }
        }

        return findPhotoUrl(playerName);
    }

    public String findPhotoUrlByOfficialId(String officialId) {
        if (officialId == null || officialId.isBlank()) {
            return null;
        }
        return resolvePhotoUrl(officialId.startsWith("p") ? officialId : "p" + officialId);
    }

    private String searchByName(String name) {
        try {
            String url = String.format(SEARCH_URL, URLEncoder.encode(name, StandardCharsets.UTF_8));
            String body = http.getWithHeaders(url, Map.of(
                    "Origin", "https://www.premierleague.com",
                    "Referer", "https://www.premierleague.com/",
                    "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            ));
            if (body == null || body.isEmpty()) {
                return null;
            }

            JsonNode root = mapper.readTree(body);
            JsonNode hits = root.path("hits").path("hit");
            if (!hits.isArray() || hits.size() == 0) {
                log.debug("[PlPhoto] no hits for {}", name);
                return null;
            }

            JsonNode picked = pickBestMatch(hits, name);
            String opta = picked.path("response").path("altIds").path("opta").asText(null);
            if (opta == null || opta.isBlank()) {
                return null;
            }

            String photoUrl = resolvePhotoUrl(opta);
            log.debug("[PlPhoto] {} -> {} -> {}", name, opta, photoUrl);
            return photoUrl;
        } catch (Exception e) {
            log.debug("[PlPhoto] searchByName failed for {}: {}", name, e.getMessage());
            return null;
        }
    }

    private String searchByAlias(String name) {
        String alias = SEARCH_ALIASES.get(name);
        if (alias == null) {
            alias = SEARCH_ALIASES.get(stripAccents(name));
        }
        if (alias == null || alias.equalsIgnoreCase(name)) {
            return null;
        }
        return searchByName(alias);
    }

    private String resolveOverridePhoto(String name) {
        String opta = OPTA_OVERRIDES.get(name);
        if (opta == null) {
            opta = OPTA_OVERRIDES.get(stripAccents(name));
        }
        if (opta == null) {
            return null;
        }
        return resolvePhotoUrl(opta);
    }

    private String resolvePhotoUrl(String opta) {
        String legacyUrl = String.format(LEGACY_PHOTO_URL_TMPL, opta);
        if (http.headOk(legacyUrl, IMAGE_HEADERS)) {
            return legacyUrl;
        }

        String numericOpta = opta.startsWith("p") ? opta.substring(1) : opta;
        String currentUrl = String.format(CURRENT_PHOTO_URL_TMPL, numericOpta);
        if (http.headOk(currentUrl, IMAGE_HEADERS)) {
            return currentUrl;
        }

        String smallUrl = String.format(CURRENT_SMALL_PHOTO_URL_TMPL, numericOpta);
        if (http.headOk(smallUrl, IMAGE_HEADERS)) {
            return smallUrl;
        }

        return null;
    }

    private String extractOptaFromPhotoUrl(String url) {
        if (!url.contains("resources.premierleague.com")
                && !url.contains("resources.premierleague.pulselive.com")) {
            return null;
        }

        int slash = url.lastIndexOf('/');
        String fileName = slash >= 0 ? url.substring(slash + 1) : url;
        int query = fileName.indexOf('?');
        if (query >= 0) {
            fileName = fileName.substring(0, query);
        }
        int dot = fileName.indexOf('.');
        String id = dot >= 0 ? fileName.substring(0, dot) : fileName;
        if (!id.matches("p?\\d+")) {
            return null;
        }
        return id.startsWith("p") ? id : "p" + id;
    }

    private static String stripAccents(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD);
        return normalized.replaceAll("\\p{M}", "")
                .replace('\u2019', '\'')
                .replace('\u2018', '\'')
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace('\u0131', 'i')
                .replace('\u0141', 'L')
                .replace('\u0142', 'l')
                .replace('\u00d8', 'O')
                .replace('\u00f8', 'o')
                .replace('\u0110', 'D')
                .replace('\u0111', 'd')
                .replace('\u015e', 'S')
                .replace('\u015f', 's')
                .replace('\u0218', 'S')
                .replace('\u0219', 's')
                .replace('\u021a', 'T')
                .replace('\u021b', 't')
                .replace("酶", "o")
                .replace("脴", "O");
    }

    private JsonNode pickBestMatch(JsonNode hits, String query) {
        String q = query.toLowerCase(Locale.ROOT);
        for (JsonNode hit : hits) {
            String display = hit.path("response").path("name").path("display").asText("");
            if (display.equalsIgnoreCase(query)) {
                return hit;
            }
        }
        for (JsonNode hit : hits) {
            String display = hit.path("response").path("name").path("display")
                    .asText("")
                    .toLowerCase(Locale.ROOT);
            if (display.contains(q) || q.contains(display)) {
                return hit;
            }
        }
        return hits.get(0);
    }
}
