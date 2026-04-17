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
 * 备用数据源：understat.com（首选备用源，国内直连无墙）
 *
 * 端点：POST https://understat.com/main/getPlayersStats/
 *       Content-Type: application/x-www-form-urlencoded
 *       body: league=EPL&season={year}
 *       header: X-Requested-With: XMLHttpRequest （否则返回 error_code=4）
 *
 * 返回 JSON 示例：
 * {
 *   "success": true,
 *   "players": [
 *     { "id":"8260", "player_name":"Erling Haaland", "games":"30", "time":"2529",
 *       "goals":"22", "assists":"7", "npg":"19", "position":"F S",
 *       "team_title":"Manchester City", "xG":"23.41...", "xA":"4.76..." , ... }
 *   ]
 * }
 *
 * 字段映射：
 *   goals        ← goals
 *   assists      ← assists
 *   penalties    ← goals - npg   (non-penalty goals → 反推)
 *   playedMatches← games
 *   position     ← position 首字母 (F/M/D/G)
 *   team         ← team_title（可能是 "Team A,Team B" 转会时）
 *
 * season 用日历年：2025/26 赛季对应 season=2025
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnderstatProvider {

    private static final String URL = "https://understat.com/main/getPlayersStats/";

    @Value("${understat.season:2025}")
    private String season;

    private final HttpClientUtil httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Cacheable(value = "fdUnderstatScorers", key = "'all'")
    public List<PlayerStat> fetchScorers() {
        List<PlayerStat> stats = new ArrayList<>();
        try {
            log.info("[Understat] Fetching players stats (season={})", season);

            Map<String, String> headers = new HashMap<>();
            headers.put("X-Requested-With", "XMLHttpRequest");
            headers.put("Referer", "https://understat.com/league/EPL");
            headers.put("Origin", "https://understat.com");
            headers.put("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
            headers.put("Accept", "application/json, text/javascript, */*; q=0.01");

            String body = httpClient.postForm(URL, "league=EPL&season=" + season, headers);
            if (body == null || body.isEmpty()) {
                log.warn("[Understat] Empty response");
                return stats;
            }

            JsonNode root = objectMapper.readTree(body);
            if (!root.path("success").asBoolean(false)) {
                log.warn("[Understat] success=false, body head: {}",
                        body.substring(0, Math.min(200, body.length())));
                return stats;
            }
            JsonNode players = root.path("players");
            if (!players.isArray()) {
                log.warn("[Understat] 'players' is not an array");
                return stats;
            }

            for (JsonNode p : players) {
                PlayerStat s = parse(p);
                if (s != null) stats.add(s);
            }
            log.info("[Understat] Parsed {} player stats", stats.size());
        } catch (Exception e) {
            log.error("[Understat] Failed to fetch stats: {}", e.getMessage(), e);
        }
        return stats;
    }

    private PlayerStat parse(JsonNode p) {
        try {
            String name = p.path("player_name").asText(null);
            if (name == null || name.isEmpty()) return null;

            int goals = p.path("goals").asInt(0);
            int assists = p.path("assists").asInt(0);
            int npg = p.path("npg").asInt(goals);          // 非点球进球；缺省就按没点球
            int penalties = Math.max(0, goals - npg);
            int games = p.path("games").asInt(0);

            String position = p.path("position").asText("");
            String teamTitle = p.path("team_title").asText(null);
            // 转会球员会出现 "Bournemouth,Manchester City"，取最后一个（当前队）
            if (teamTitle != null && teamTitle.contains(",")) {
                String[] split = teamTitle.split(",");
                teamTitle = split[split.length - 1].trim();
            }

            Long playerId = null;
            String idText = p.path("id").asText(null);
            if (idText != null) {
                try { playerId = Long.parseLong(idText); } catch (NumberFormatException ignored) {}
            }

            return new PlayerStat(
                    null,                                   // rank - service 层赋值
                    playerId,
                    name,
                    null,                                   // chineseName
                    null,                                   // nationality (understat 没有)
                    mapPosition(position),
                    mapPositionCn(position),
                    null,                                   // shirtNumber
                    null,                                   // teamId
                    teamTitle,
                    teamTitle,
                    mapTeamToChinese(teamTitle),
                    null,                                   // teamCrest
                    goals, assists, penalties, games
            );
        } catch (Exception e) {
            log.debug("[Understat] parse error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * position 字段格式：空格分隔的位置码，主位置是第一个。
     *   "F S" = Forward (Striker 子位)
     *   "F M" = Forward/Midfielder
     *   "M"   = Midfielder
     *   "D"   = Defender
     *   "GK"  = Goalkeeper
     */
    private String mapPosition(String pos) {
        if (pos == null || pos.isEmpty()) return "";
        String primary = pos.split("\\s+")[0].trim().toUpperCase();
        return switch (primary) {
            case "GK", "G" -> "Goalkeeper";
            case "D" -> "Defender";
            case "M" -> "Midfielder";
            case "F" -> "Attacker";
            default -> primary;
        };
    }

    private String mapPositionCn(String pos) {
        if (pos == null || pos.isEmpty()) return "";
        String primary = pos.split("\\s+")[0].trim().toUpperCase();
        return switch (primary) {
            case "GK", "G" -> "门将";
            case "D" -> "后卫";
            case "M" -> "中场";
            case "F" -> "前锋";
            default -> primary;
        };
    }

    /** understat 队名 → 中文（拼写与英超官方接近，直接复用 fbref 映射的超集） */
    private String mapTeamToChinese(String team) {
        if (team == null) return null;
        return TEAM_ZH.getOrDefault(team, team);
    }

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
        TEAM_ZH.put("Newcastle United", "纽卡斯尔联");
        TEAM_ZH.put("Nottingham Forest", "诺丁汉森林");
        TEAM_ZH.put("Sheffield United", "谢菲尔德联");
        TEAM_ZH.put("Southampton", "南安普顿");
        TEAM_ZH.put("Sunderland", "桑德兰");
        TEAM_ZH.put("Tottenham", "热刺");
        TEAM_ZH.put("West Ham", "西汉姆联");
        TEAM_ZH.put("Wolverhampton Wanderers", "狼队");
    }
}
