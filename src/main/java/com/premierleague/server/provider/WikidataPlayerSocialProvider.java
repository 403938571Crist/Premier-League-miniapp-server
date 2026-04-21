package com.premierleague.server.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.premierleague.server.model.PlayerSocialCandidate;
import com.premierleague.server.util.HttpClientUtil;
import com.premierleague.server.util.PlayerNameNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class WikidataPlayerSocialProvider {

    private static final String SEARCH_URL =
            "https://www.wikidata.org/w/api.php?action=wbsearchentities&search=%s&language=en&format=json"
                    + "&limit=5&type=item&origin=*";

    private static final String ENTITY_URL = "https://www.wikidata.org/wiki/Special:EntityData/%s.json";

    private final HttpClientUtil httpClientUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<PlayerSocialCandidate> fetchProfiles(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return List.of();
        }

        try {
            String searchUrl = SEARCH_URL.formatted(URLEncoder.encode(playerName, StandardCharsets.UTF_8));
            String searchBody = httpClientUtil.get(searchUrl);
            if (searchBody == null || searchBody.isBlank()) {
                return List.of();
            }

            JsonNode searchRoot = objectMapper.readTree(searchBody);
            JsonNode matched = pickBestEntity(playerName, searchRoot.path("search"));
            if (matched == null) {
                return List.of();
            }

            String entityId = matched.path("id").asText(null);
            if (entityId == null || entityId.isBlank()) {
                return List.of();
            }

            String entityBody = httpClientUtil.get(ENTITY_URL.formatted(entityId));
            if (entityBody == null || entityBody.isBlank()) {
                return List.of();
            }

            JsonNode entityRoot = objectMapper.readTree(entityBody);
            JsonNode entity = entityRoot.path("entities").path(entityId);
            return extractProfiles(entity.path("claims"));
        } catch (Exception e) {
            log.warn("[WikidataPlayerSocialProvider] failed to fetch {}: {}", playerName, e.getMessage());
            return List.of();
        }
    }

    private JsonNode pickBestEntity(String playerName, JsonNode results) {
        if (!results.isArray()) {
            return null;
        }

        String queryKey = PlayerNameNormalizer.normalize(playerName);

        for (JsonNode result : results) {
            if (isFootballResult(result) && queryKey.equals(PlayerNameNormalizer.normalize(result.path("label").asText("")))) {
                return result;
            }
        }

        for (JsonNode result : results) {
            if (isFootballResult(result)) {
                return result;
            }
        }

        return null;
    }

    private boolean isFootballResult(JsonNode result) {
        String description = result.path("description").asText("").toLowerCase(Locale.ROOT);
        return description.contains("football") || description.contains("soccer");
    }

    private List<PlayerSocialCandidate> extractProfiles(JsonNode claims) {
        List<PlayerSocialCandidate> profiles = new ArrayList<>();

        String xHandle = readClaimValue(claims, "P2002");
        if (hasText(xHandle)) {
            profiles.add(new PlayerSocialCandidate(
                    "X",
                    formatHandle(xHandle),
                    "https://x.com/" + xHandle,
                    null,
                    null,
                    null
            ));
        }

        String instagramHandle = readClaimValue(claims, "P2003");
        if (hasText(instagramHandle)) {
            profiles.add(new PlayerSocialCandidate(
                    "Instagram",
                    formatHandle(instagramHandle),
                    "https://www.instagram.com/" + instagramHandle + "/",
                    null,
                    null,
                    null
            ));
        }

        String facebookHandle = readClaimValue(claims, "P2013");
        if (hasText(facebookHandle)) {
            profiles.add(new PlayerSocialCandidate(
                    "Facebook",
                    facebookHandle,
                    "https://www.facebook.com/" + facebookHandle,
                    null,
                    null,
                    null
            ));
        }

        return profiles;
    }

    private String readClaimValue(JsonNode claims, String propertyId) {
        JsonNode entries = claims.path(propertyId);
        if (!entries.isArray() || entries.isEmpty()) {
            return null;
        }
        JsonNode value = entries.get(0).path("mainsnak").path("datavalue").path("value");
        return value.isTextual() ? value.asText() : null;
    }

    private String formatHandle(String handle) {
        return handle.startsWith("@") ? handle : "@" + handle;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
