package com.premierleague.server.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.premierleague.server.entity.Team;
import com.premierleague.server.util.HttpClientUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pulselive 公开 API 积分榜数据源（FootballDataProvider 的 token-free fallback）
 *
 * 背景：
 *   Football-Data.org v4 要 X-Auth-Token，免费额度 10 req/min。一旦拿不到 token（本项目当前情况），
 *   整个 standings/teams/matches 链路会同步失败。
 *
 * 策略：
 *   用 Premier League 官网 SPA 背后的公开 API（无需鉴权，只要带 Origin/Referer 头）拿积分榜，
 *   组装成同构 Team 列表，交给调用方按 name / shortName 去 upsert 已有行。
 *
 * URL shape（从 premierleague.com 逆向 + 实测验证）：
 *   1. 取当前赛季 id：
 *      GET https://footballapi.pulselive.com/football/competitions/1/compseasons
 *      → content[0] 是最新赛季（例如 {id:777, label:"2025/26"}）
 *   2. 拉积分榜：
 *      GET https://footballapi.pulselive.com/football/standings
 *          ?compSeasons={id}&altIds=true&detail=2&FOOTBALL_COMPETITION=1
 *      → tables[0].entries[] 即 20 支球队的 overall 战绩
 *
 * 注意：
 *   - 返回的 Team.apiId 是 null（Pulselive team.id 和 Football-Data.org apiId 不兼容，
 *     不塞进去免得污染现有行）；upsert 方需要按 name/shortName 匹配。
 *   - shortName 这里填 Pulselive 的 entry.team.shortName（"Man Utd" / "Spurs" / "Nott'm Forest" 等）。
 *   - chineseName 用内置 name->中文 映射兜底；进不了映射就留 shortName。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PulseliveStandingsProvider {

    @Value("${pulselive.standings.base-url:https://footballapi.pulselive.com/football}")
    private String baseUrl;

    /** Premier League 在 Pulselive 的 competition id (固定值 1)。 */
    @Value("${pulselive.standings.competition-id:1}")
    private String competitionId;

    /**
     * 可选：强制指定 compSeason id。留空表示从 /compseasons 动态取最新。
     * 2025/26=777, 2024/25=719, 2023/24=578（回溯用）。
     */
    @Value("${pulselive.standings.season-id:}")
    private String configuredSeasonId;

    private final HttpClientUtil httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, String> NAME_CN = buildNameChineseMap();

    /**
     * 拉取最新英超积分榜。返回空列表表示失败（上游日志里应该能看到原因）。
     */
    public List<Team> fetchStandings() {
        String seasonId = resolveCurrentSeasonId();
        if (seasonId == null || seasonId.isBlank()) {
            log.warn("[PulseliveStandings] Could not resolve current compSeason id");
            return new ArrayList<>();
        }

        String url = baseUrl + "/standings?compSeasons=" + seasonId
                + "&altIds=true&detail=2&FOOTBALL_COMPETITION=1";
        log.info("[PulseliveStandings] Fetching standings: {}", url);

        String body = httpClient.getWithHeaders(url, pulseliveHeaders());
        if (body == null || body.isEmpty()) {
            log.warn("[PulseliveStandings] Empty response body from {}", url);
            return new ArrayList<>();
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode tables = root.path("tables");
            if (!tables.isArray() || tables.isEmpty()) {
                log.warn("[PulseliveStandings] No tables in response");
                return new ArrayList<>();
            }
            JsonNode entries = tables.get(0).path("entries");
            if (!entries.isArray()) {
                log.warn("[PulseliveStandings] No entries array in table[0]");
                return new ArrayList<>();
            }

            List<Team> teams = new ArrayList<>();
            for (JsonNode entry : entries) {
                Team t = parseEntry(entry);
                if (t != null) teams.add(t);
            }
            log.info("[PulseliveStandings] Parsed {} teams from compSeason={} (label={})",
                    teams.size(), seasonId,
                    root.path("compSeason").path("label").asText(""));
            return teams;
        } catch (Exception e) {
            log.error("[PulseliveStandings] Failed to parse standings JSON: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 解析 /football/competitions/1/compseasons 的 content[0] 作为当前赛季。
     * 若配置里写死了 configuredSeasonId，直接用。
     */
    private String resolveCurrentSeasonId() {
        if (configuredSeasonId != null && !configuredSeasonId.isBlank()) {
            return configuredSeasonId.trim();
        }
        String url = baseUrl + "/competitions/" + competitionId + "/compseasons";
        String body = httpClient.getWithHeaders(url, pulseliveHeaders());
        if (body == null || body.isEmpty()) {
            log.warn("[PulseliveStandings] Empty compseasons response");
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode content = root.path("content");
            if (content.isArray() && !content.isEmpty()) {
                // content[0] 是最新赛季
                JsonNode latest = content.get(0);
                long id = latest.path("id").asLong();
                if (id > 0) {
                    log.info("[PulseliveStandings] Resolved compSeason id={} label='{}'",
                            id, latest.path("label").asText(""));
                    return String.valueOf(id);
                }
            }
        } catch (Exception e) {
            log.warn("[PulseliveStandings] Failed to parse compseasons: {}", e.getMessage());
        }
        return null;
    }

    private Team parseEntry(JsonNode entry) {
        try {
            JsonNode teamNode = entry.path("team");
            JsonNode overall = entry.path("overall");

            Team team = new Team();
            // apiId 留空 — Pulselive id 和 Football-Data.org 不兼容，强行塞会污染 DB。
            team.setApiId(null);

            String fullName = teamNode.path("name").asText("");
            String shortName = teamNode.path("shortName").asText("");
            String abbr = teamNode.path("club").path("abbr").asText("");

            team.setName(fullName);
            team.setShortName(shortName.isBlank() ? abbr : shortName);
            team.setChineseName(resolveChineseName(fullName, shortName));

            // Pulselive 不给队徽 URL，走 resources.premierleague.com 规约拼一个（有优于无）
            String optaId = teamNode.path("altIds").path("opta").asText("");
            if (!optaId.isBlank()) {
                // t14 -> 14，PL 官方队徽模板
                String digits = optaId.replaceAll("[^0-9]", "");
                if (!digits.isBlank()) {
                    team.setCrestUrl("https://resources.premierleague.com/premierleague/badges/50/t" + digits + ".png");
                }
            }

            team.setPosition(entry.path("position").asInt());
            team.setPlayedGames(overall.path("played").asInt());
            team.setWon(overall.path("won").asInt());
            team.setDraw(overall.path("drawn").asInt());
            team.setLost(overall.path("lost").asInt());
            team.setPoints(overall.path("points").asInt());
            team.setGoalsFor(overall.path("goalsFor").asInt());
            team.setGoalsAgainst(overall.path("goalsAgainst").asInt());
            team.setGoalDifference(overall.path("goalsDifference").asInt());

            return team;
        } catch (Exception e) {
            log.warn("[PulseliveStandings] Failed to parse entry: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, String> pulseliveHeaders() {
        Map<String, String> h = new HashMap<>();
        h.put("Origin", "https://www.premierleague.com");
        h.put("Referer", "https://www.premierleague.com/");
        h.put("Accept", "application/json, text/plain, */*");
        h.put("Accept-Language", "en-US,en;q=0.9");
        h.put("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36");
        return h;
    }

    /**
     * name / shortName → 中文名。没命中就返回 shortName 兜底（和 FootballDataProvider 一致）。
     */
    private String resolveChineseName(String fullName, String shortName) {
        String cn = NAME_CN.get(fullName);
        if (cn != null) return cn;
        cn = NAME_CN.get(shortName);
        if (cn != null) return cn;
        String normFull = normalize(fullName);
        cn = NAME_CN.get(normFull);
        if (cn != null) return cn;
        return shortName == null || shortName.isBlank() ? fullName : shortName;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT)
                .replace("&", "and")
                .replace(".", "")
                .replace(",", "")
                .replace("'", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * 内置 Pulselive 球队名 → 中文名 映射。
     * 和 FootballDataProvider#buildTeamNameChineseMap 保持一致，并补上 Pulselive 特有写法
     * （Man Utd / Spurs / Nott'm Forest 等）。
     */
    private static Map<String, String> buildNameChineseMap() {
        Map<String, String> m = new HashMap<>();
        add(m, "阿森纳", "Arsenal");
        add(m, "阿斯顿维拉", "Aston Villa");
        add(m, "伯恩茅斯", "Bournemouth");
        add(m, "布伦特福德", "Brentford");
        add(m, "布莱顿", "Brighton", "Brighton & Hove Albion", "Brighton Hove Albion", "Brighton and Hove Albion");
        add(m, "伯恩利", "Burnley");
        add(m, "切尔西", "Chelsea");
        add(m, "水晶宫", "Crystal Palace");
        add(m, "埃弗顿", "Everton");
        add(m, "富勒姆", "Fulham");
        add(m, "伊普斯维奇镇", "Ipswich", "Ipswich Town");
        add(m, "利兹联", "Leeds", "Leeds United", "Leeds Utd");
        add(m, "莱斯特城", "Leicester", "Leicester City");
        add(m, "利物浦", "Liverpool");
        add(m, "卢顿城", "Luton", "Luton Town");
        add(m, "曼城", "Man City", "Manchester City");
        add(m, "曼联", "Man Utd", "Man United", "Manchester United");
        add(m, "纽卡斯尔", "Newcastle", "Newcastle United");
        add(m, "诺丁汉森林", "Nottingham Forest", "Nott'm Forest", "Nottm Forest", "Nottingham");
        add(m, "谢菲联", "Sheffield United", "Sheffield Utd");
        add(m, "南安普顿", "Southampton");
        add(m, "桑德兰", "Sunderland");
        add(m, "热刺", "Spurs", "Tottenham", "Tottenham Hotspur");
        add(m, "西汉姆联", "West Ham", "West Ham United");
        add(m, "狼队", "Wolves", "Wolverhampton", "Wolverhampton Wanderers");
        return m;
    }

    private static void add(Map<String, String> m, String cn, String... aliases) {
        for (String a : aliases) {
            if (a == null || a.isBlank()) continue;
            m.put(a, cn);
            m.put(normalize(a), cn);
        }
    }
}
