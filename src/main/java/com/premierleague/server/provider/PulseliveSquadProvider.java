package com.premierleague.server.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.premierleague.server.util.HttpClientUtil;
import com.premierleague.server.util.PlayerNameNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PulseliveSquadProvider {

    private static final Map<String, String> REQUEST_HEADERS = Map.of(
            "Accept", "application/json, text/plain, */*",
            "Origin", "https://www.premierleague.com",
            "Referer", "https://www.premierleague.com/",
            "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    );

    @Value("${pulselive.base-url:https://sdp-prem-prod.premier-league-prod.pulselive.com/api}")
    private String baseUrl = "https://sdp-prem-prod.premier-league-prod.pulselive.com/api";

    @Value("${pulselive.competition-id:8}")
    private String competitionId = "8";

    @Value("${pulselive.season-id:2025}")
    private String seasonId = "2025";

    private final HttpClientUtil httpClientUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile Map<String, String> teamIdCache = Map.of();

    public List<SquadPlayer> fetchSquad(String teamName) {
        String teamId = resolveTeamId(teamName);
        if (teamId == null) {
            return List.of();
        }

        String url = baseUrl + "/v2/competitions/" + competitionId + "/seasons/" + seasonId
                + "/teams/" + teamId + "/squad";
        String body = httpClientUtil.getWithHeaders(url, REQUEST_HEADERS);
        if (body == null || body.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode players = root.path("players");
            if (!players.isArray()) {
                return List.of();
            }

            return java.util.stream.StreamSupport.stream(players.spliterator(), false)
                    .map(this::toSquadPlayer)
                    .filter(player -> player.displayName() != null && !player.displayName().isBlank())
                    .toList();
        } catch (Exception e) {
            log.warn("[PulseliveSquadProvider] failed to parse squad for {}: {}", teamName, e.getMessage());
            return List.of();
        }
    }

    private String resolveTeamId(String teamName) {
        if (teamName == null || teamName.isBlank()) {
            return null;
        }

        Map<String, String> cached = teamIdCache;
        if (cached.isEmpty()) {
            cached = loadTeamIds();
            teamIdCache = cached;
        }
        return cached.get(normalizeTeamKey(teamName));
    }

    private Map<String, String> loadTeamIds() {
        String url = baseUrl + "/v1/competitions/" + competitionId + "/seasons/" + seasonId + "/teams?_limit=50";
        String body = httpClientUtil.getWithHeaders(url, REQUEST_HEADERS);
        if (body == null || body.isBlank()) {
            return Map.of();
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode teams = root.path("data");
            if (!teams.isArray()) {
                return Map.of();
            }

            Map<String, String> resolved = new HashMap<>();
            for (JsonNode team : teams) {
                String id = team.path("id").asText(null);
                if (id == null || id.isBlank()) {
                    continue;
                }
                putTeamKey(resolved, team.path("name").asText(null), id);
                putTeamKey(resolved, team.path("shortName").asText(null), id);
                putTeamKey(resolved, team.path("abbr").asText(null), id);
            }
            return resolved;
        } catch (Exception e) {
            log.warn("[PulseliveSquadProvider] failed to resolve teams: {}", e.getMessage());
            return Map.of();
        }
    }

    private void putTeamKey(Map<String, String> resolved, String teamName, String id) {
        if (teamName == null || teamName.isBlank()) {
            return;
        }
        resolved.putIfAbsent(normalizeTeamKey(teamName), id);
    }

    private String normalizeTeamKey(String teamName) {
        return PlayerNameNormalizer.normalize(teamName)
                .replace(" football club", "")
                .replace(" fc", "")
                .replace(" afc", "")
                .replace(" and ", " ")
                .replace(" a villa", " aston villa")
                .replace(" c palace", " crystal palace")
                .trim();
    }

    private SquadPlayer toSquadPlayer(JsonNode node) {
        JsonNode name = node.path("name");
        JsonNode country = node.path("country");
        JsonNode dates = node.path("dates");

        return new SquadPlayer(
                node.path("id").asText(null),
                name.path("display").asText(null),
                node.path("shirtNum").isMissingNode() || node.path("shirtNum").isNull()
                        ? null
                        : node.path("shirtNum").asText(),
                node.path("position").asText(null),
                country.path("country").asText(null),
                parseDate(dates.path("birth").asText(null)),
                node.path("height").isMissingNode() || node.path("height").isNull() ? null : node.path("height").asInt(),
                node.path("weight").isMissingNode() || node.path("weight").isNull() ? null : node.path("weight").asInt(),
                node.path("preferredFoot").asText(null)
        );
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    public record SquadPlayer(
            String officialId,
            String displayName,
            String shirtNumber,
            String position,
            String nationality,
            LocalDate dateOfBirth,
            Integer heightCm,
            Integer weightKg,
            String preferredFoot
    ) {
    }
}
