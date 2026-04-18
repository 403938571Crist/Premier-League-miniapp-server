package com.premierleague.server.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.premierleague.server.model.PlayerStat;
import com.premierleague.server.util.HttpClientUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API-Football (api-sports.io / RapidAPI) 数据源
 *
 * 免费档配额：100 req/天 + 30 req/分钟
 * 使用场景：football-data.org 免费档不给 /scorers (403) 时的首选兜底
 *
 * 端点：
 *   GET /players/topscorers?league=39&season=2024
 *   GET /players/topassists?league=39&season=2024
 *
 * 认证：header `x-apisports-key: <KEY>`
 *
 * 未配置 key 时 fetch* 直接返回空列表（让 service 跳过此源）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiFootballProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClientUtil httpClient;

    @Value("${api-football.base-url:https://v3.football.api-sports.io}")
    private String baseUrl;

    @Value("${api-football.key:}")
    private String apiKey;

    @Value("${api-football.league-id:39}")
    private int leagueId;

    @Value("${api-football.season:2024}")
    private int season;

    /**
     * 射手榜 - /players/topscorers
     */
    @Cacheable(value = "apiFootballScorers", key = "#root.target.season + '-' + #root.target.leagueId")
    public List<PlayerStat> fetchScorers() {
        if (isDisabled()) {
            log.debug("[ApiFootball] No API key configured, skipping scorers");
            return new ArrayList<>();
        }
        String url = String.format("%s/players/topscorers?league=%d&season=%d",
                baseUrl, leagueId, season);
        log.info("[ApiFootball] Fetching top scorers: league={}, season={}", leagueId, season);
        return fetchAndParse(url);
    }

    /**
     * 助攻榜 - /players/topassists
     */
    @Cacheable(value = "apiFootballAssists", key = "#root.target.season + '-' + #root.target.leagueId")
    public List<PlayerStat> fetchAssists() {
        if (isDisabled()) {
            log.debug("[ApiFootball] No API key configured, skipping assists");
            return new ArrayList<>();
        }
        String url = String.format("%s/players/topassists?league=%d&season=%d",
                baseUrl, leagueId, season);
        log.info("[ApiFootball] Fetching top assists: league={}, season={}", leagueId, season);
        return fetchAndParse(url);
    }

    public boolean isDisabled() {
        return apiKey == null || apiKey.isBlank();
    }

    private List<PlayerStat> fetchAndParse(String url) {
        List<PlayerStat> result = new ArrayList<>();
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("x-apisports-key", apiKey);
            headers.put("Accept", "application/json");

            String body = httpClient.getWithHeaders(url, headers);
            if (body == null || body.isEmpty()) {
                log.warn("[ApiFootball] Empty response from {}", url);
                return result;
            }

            JsonNode root = MAPPER.readTree(body);

            // api-sports 返回结构：
            //   { errors: {...} | [], response: [ { player: {...}, statistics: [ {...} ] }, ... ] }
            JsonNode errors = root.path("errors");
            if (errors.isObject() && errors.size() > 0) {
                log.error("[ApiFootball] API returned errors: {}", errors);
                return result;
            }

            JsonNode response = root.path("response");
            if (!response.isArray() || response.size() == 0) {
                log.warn("[ApiFootball] No 'response' array in payload (results={})",
                        root.path("results").asInt(-1));
                return result;
            }

            for (JsonNode entry : response) {
                PlayerStat stat = parseEntry(entry);
                if (stat != null) result.add(stat);
            }

            log.info("[ApiFootball] Parsed {} rows from {}", result.size(), url);
        } catch (Exception e) {
            log.error("[ApiFootball] Fetch failed for {}: {}", url, e.getMessage(), e);
        }
        return result;
    }

    private PlayerStat parseEntry(JsonNode entry) {
        try {
            JsonNode player = entry.path("player");
            JsonNode statsArr = entry.path("statistics");
            if (!statsArr.isArray() || statsArr.size() == 0) return null;
            JsonNode stat = statsArr.get(0);

            Long playerId = player.path("id").isNumber() ? player.path("id").asLong() : null;
            String playerName = player.path("name").asText(null);
            if (playerName == null || playerName.isBlank()) return null;
            String nationality = player.path("nationality").asText(null);
            String photoUrl = player.path("photo").asText(null);

            JsonNode teamNode = stat.path("team");
            Long teamId = teamNode.path("id").isNumber() ? teamNode.path("id").asLong() : null;
            String teamName = teamNode.path("name").asText(null);
            String teamLogo = teamNode.path("logo").asText(null);

            String position = stat.path("games").path("position").asText(null);
            Integer playedMatches = intOrNull(stat.path("games").path("appearences"));
            Integer goals = intOrNull(stat.path("goals").path("total"));
            Integer assists = intOrNull(stat.path("goals").path("assists"));
            Integer penalties = intOrNull(stat.path("penalty").path("scored"));

            return new PlayerStat(
                    null,
                    playerId, playerName, null, nationality,
                    normalizePosition(position), positionToChinese(position), null,
                    teamId, teamName, teamName, teamToChinese(teamName), teamLogo,
                    nz(goals), nz(assists), nz(penalties), nz(playedMatches),
                    photoUrl
            );
        } catch (Exception e) {
            log.debug("[ApiFootball] Failed to parse entry: {}", e.getMessage());
            return null;
        }
    }

    private Integer intOrNull(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode() ? null : node.asInt();
    }

    private int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private String normalizePosition(String apiPos) {
        if (apiPos == null) return "";
        return switch (apiPos.toLowerCase()) {
            case "goalkeeper" -> "Goalkeeper";
            case "defender" -> "Defender";
            case "midfielder" -> "Midfielder";
            case "attacker" -> "Attacker";
            default -> apiPos;
        };
    }

    private String positionToChinese(String apiPos) {
        if (apiPos == null) return "";
        return switch (apiPos.toLowerCase()) {
            case "goalkeeper" -> "门将";
            case "defender" -> "后卫";
            case "midfielder" -> "中场";
            case "attacker" -> "前锋";
            default -> apiPos;
        };
    }

    // 与 FbrefProvider 保持一致的中文队名映射（api-football 的英文队名略有差异）
    private static final Map<String, String> TEAM_ZH = new HashMap<>();
    static {
        TEAM_ZH.put("Arsenal", "阿森纳");
        TEAM_ZH.put("Aston Villa", "阿斯顿维拉");
        TEAM_ZH.put("Bournemouth", "伯恩茅斯");
        TEAM_ZH.put("Brentford", "布伦特福德");
        TEAM_ZH.put("Brighton", "布莱顿");
        TEAM_ZH.put("Burnley", "伯恩利");
        TEAM_ZH.put("Chelsea", "切尔西");
        TEAM_ZH.put("Crystal Palace", "水晶宫");
        TEAM_ZH.put("Everton", "埃弗顿");
        TEAM_ZH.put("Fulham", "富勒姆");
        TEAM_ZH.put("Ipswich", "伊普斯维奇");
        TEAM_ZH.put("Leeds", "利兹联");
        TEAM_ZH.put("Leicester", "莱斯特城");
        TEAM_ZH.put("Liverpool", "利物浦");
        TEAM_ZH.put("Luton", "卢顿");
        TEAM_ZH.put("Manchester City", "曼城");
        TEAM_ZH.put("Manchester United", "曼联");
        TEAM_ZH.put("Newcastle", "纽卡斯尔联");
        TEAM_ZH.put("Nottingham Forest", "诺丁汉森林");
        TEAM_ZH.put("Sheffield Utd", "谢菲尔德联");
        TEAM_ZH.put("Southampton", "南安普顿");
        TEAM_ZH.put("Sunderland", "桑德兰");
        TEAM_ZH.put("Tottenham", "热刺");
        TEAM_ZH.put("West Ham", "西汉姆联");
        TEAM_ZH.put("Wolves", "狼队");
    }

    private String teamToChinese(String name) {
        if (name == null) return null;
        return TEAM_ZH.getOrDefault(name, name);
    }
}
