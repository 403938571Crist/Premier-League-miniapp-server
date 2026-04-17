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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 备用数据源：Premier League 官方（pulselive v3 leaderboard）
 *
 * URL shape（从 premierleague.com SPA bundle-es.min.js 1.44.5 逆向得到）：
 *   GET https://sdp-prem-test.platform-eu-test.pulselive.com/api
 *       /v3/competitions/{compId}/seasons/{seasonId}/players/stats/leaderboard?page=&pageSize=
 *
 *   compId = 8   (Premier League, 来源：PREMIER_LEAGUE:"8")
 *   seasonId = 2025   (来源：HTML 中 ACTIVE_PL_SEASON_ID = '2025')
 *
 * 注意事项：
 *   - 该 host 在国内会被 DNS 污染成 28.0.0.139（GFW 典型行为），本地 dev 无法直连；
 *     需要在国内机器用代理 / VPN，或部署到微信云托管（香港/新加坡出口）才能正常连通。
 *   - 老 host footballapi.pulselive.com 的 /stats/player/goals 路径已被废弃（返回 200 + 0 字节）。
 *   - 响应体 JSON 结构尚未实测（见上），本类采用多候选字段的宽松解析，
 *     首次调用会把原始 body 前 1500 字节 INFO 级打出，部署后即可根据日志对齐字段名。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PulseliveProvider {

    /** 允许通过配置覆盖 base（切备用镜像 / 配代理走）。默认就是 SPA bundle 里找到的那个 host。 */
    @Value("${pulselive.base-url:https://sdp-prem-test.platform-eu-test.pulselive.com/api}")
    private String baseUrl;

    /** Premier League 在 pulselive 里的 competition id。来源：bundle 里 PREMIER_LEAGUE:"8"。 */
    @Value("${pulselive.competition-id:8}")
    private String competitionId;

    /** 当前赛季 id，来源：premierleague.com HTML 里 window.ACTIVE_PL_SEASON_ID。 */
    @Value("${pulselive.season-id:2025}")
    private String seasonId;

    /** 单次抓多少行（联赛在册球员 ~500，留够就行）。 */
    @Value("${pulselive.page-size:600}")
    private int pageSize;

    private final HttpClientUtil httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 首次解析成功后置位，避免后续调用重复打整包日志。 */
    private volatile boolean sampleLogged = false;

    @Cacheable(value = "fdPulseliveScorers", key = "'all'")
    public List<PlayerStat> fetchScorers() {
        String url = baseUrl
                + "/v3/competitions/" + competitionId
                + "/seasons/" + seasonId
                + "/players/stats/leaderboard"
                + "?page=0&pageSize=" + pageSize;
        log.info("[Pulselive] Fetching leaderboard: {}", url);

        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("Accept-Language", "en-US,en;q=0.9");
        headers.put("Origin", "https://www.premierleague.com");
        headers.put("Referer", "https://www.premierleague.com/");

        String body = httpClient.getWithHeaders(url, headers);
        if (body == null || body.isEmpty()) {
            log.warn("[Pulselive] Empty response");
            return new ArrayList<>();
        }

        // 第一次拿到 body 时 dump 一段，部署后看一眼就能把 parseRow 的字段名对齐
        if (!sampleLogged) {
            log.info("[Pulselive] Sample body (first 1500 chars): {}",
                    body.substring(0, Math.min(1500, body.length())));
            sampleLogged = true;
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode rows = locateRows(root);
            if (rows == null || !rows.isArray()) {
                log.warn("[Pulselive] Could not locate rows array; top-level keys: {}", topLevelKeys(root));
                return new ArrayList<>();
            }
            List<PlayerStat> stats = new ArrayList<>();
            for (JsonNode row : rows) {
                PlayerStat s = parseRow(row);
                if (s != null) stats.add(s);
            }
            log.info("[Pulselive] Parsed {} player stats", stats.size());
            return stats;
        } catch (Exception e) {
            log.error("[Pulselive] Failed to parse leaderboard JSON: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 宽松定位行数组。已知 RTK-Query 常见包装有：
     *   root -> content[]                           （老 pulselive）
     *   root -> stats.content[]                     （stats/ranks）
     *   root -> data[] / items[] / entities[]       （通用）
     * 先按优先级找；都没有就尝试递归找第一个 ≥ 10 长度的数组。
     */
    private JsonNode locateRows(JsonNode root) {
        String[] candidates = {"content", "data", "items", "entities", "results"};
        for (String key : candidates) {
            if (root.hasNonNull(key) && root.get(key).isArray()) return root.get(key);
        }
        if (root.hasNonNull("stats") && root.get("stats").hasNonNull("content")) {
            return root.get("stats").get("content");
        }
        // 最后兜底：深度优先找第一个长度 > 5 的数组
        return findFirstArray(root, 5);
    }

    private JsonNode findFirstArray(JsonNode node, int minLen) {
        if (node.isArray() && node.size() > minLen) return node;
        if (node.isObject()) {
            Iterator<JsonNode> it = node.elements();
            while (it.hasNext()) {
                JsonNode found = findFirstArray(it.next(), minLen);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * 解析单行。字段名猜测（首次拿到 body 后需按实际日志收敛）：
     *   玩家：row.player.name.display | row.player.fullName | row.owner.name.display
     *   球队：row.team.name            | row.player.currentTeam.name
     *   数值：row.stats.goals / row.stats.assists / row.stats.appearances
     *         或 row.goals / row.value（老 ranks 响应是 rank + value）
     */
    private PlayerStat parseRow(JsonNode row) {
        try {
            JsonNode player = firstNonNull(row, "player", "owner");
            if (player == null) return null;

            String playerName = text(player, "name", "display");
            if (playerName == null) playerName = text(player, "fullName");
            if (playerName == null) playerName = text(player, "displayName");
            if (playerName == null) return null;

            JsonNode team = firstNonNull(row, "team");
            if (team == null && player.hasNonNull("currentTeam")) team = player.get("currentTeam");

            JsonNode stats = firstNonNull(row, "stats");
            Integer goals = intAt(stats, "goals");
            if (goals == null) goals = intAt(row, "goals");
            if (goals == null) goals = intAt(row, "value"); // /ranks 响应

            Integer assists = intAt(stats, "assists");
            if (assists == null) assists = intAt(row, "assists");

            Integer penalties = intAt(stats, "goalsFromPenalties");
            if (penalties == null) penalties = intAt(stats, "penaltyGoals");
            if (penalties == null) penalties = intAt(row, "penalties");

            Integer games = intAt(stats, "appearances");
            if (games == null) games = intAt(stats, "games");
            if (games == null) games = intAt(row, "appearances");

            String nationality = text(player, "nationality", "country")
                    != null ? text(player, "nationality", "country")
                    : text(player, "country");

            String position = text(player, "info", "position");
            if (position == null) position = text(player, "position");

            return new PlayerStat(
                    null,
                    parsePlayerId(row, player),
                    playerName,
                    null,                       // chineseName
                    nationality,
                    mapPosition(position),
                    mapPositionCn(position),
                    intAt(player, "shirtNum"),
                    parseTeamId(team),
                    text(team, "name"),
                    text(team, "shortName") != null ? text(team, "shortName") : text(team, "name"),
                    null,                       // teamChineseName - 后续用现有 TeamProvider 映射
                    text(team, "crestUrl"),     // teamCrest
                    nz(goals), nz(assists), nz(penalties), nz(games)
            );
        } catch (Exception e) {
            log.debug("[Pulselive] Row parse error: {}", e.getMessage());
            return null;
        }
    }

    private Long parsePlayerId(JsonNode row, JsonNode player) {
        Long id = longAt(player, "id");
        if (id != null) return id;
        id = longAt(row, "playerId");
        if (id != null) return id;
        String alt = text(player, "altIds", "opta");
        if (alt != null) {
            String digits = alt.replaceAll("\\D", "");
            if (!digits.isEmpty()) try { return Long.parseLong(digits); } catch (Exception ignored) {}
        }
        return null;
    }

    private Long parseTeamId(JsonNode team) {
        if (team == null) return null;
        Long id = longAt(team, "id");
        if (id != null) return id;
        String alt = text(team, "altIds", "opta");
        if (alt != null) {
            String digits = alt.replaceAll("\\D", "");
            if (!digits.isEmpty()) try { return Long.parseLong(digits); } catch (Exception ignored) {}
        }
        return null;
    }

    // ---------- JSON helpers ----------

    private String text(JsonNode node, String... path) {
        JsonNode cur = node;
        for (String p : path) {
            if (cur == null || cur.isMissingNode() || !cur.hasNonNull(p)) return null;
            cur = cur.get(p);
        }
        return cur == null || cur.isNull() ? null : cur.asText();
    }

    private Integer intAt(JsonNode node, String... path) {
        JsonNode cur = node;
        for (String p : path) {
            if (cur == null || cur.isMissingNode() || !cur.hasNonNull(p)) return null;
            cur = cur.get(p);
        }
        return cur == null || cur.isNull() ? null : cur.asInt();
    }

    private Long longAt(JsonNode node, String... path) {
        JsonNode cur = node;
        for (String p : path) {
            if (cur == null || cur.isMissingNode() || !cur.hasNonNull(p)) return null;
            cur = cur.get(p);
        }
        return cur == null || cur.isNull() ? null : cur.asLong();
    }

    private JsonNode firstNonNull(JsonNode node, String... keys) {
        for (String k : keys) if (node.hasNonNull(k)) return node.get(k);
        return null;
    }

    private List<String> topLevelKeys(JsonNode root) {
        List<String> keys = new ArrayList<>();
        root.fieldNames().forEachRemaining(keys::add);
        return keys;
    }

    private int nz(Integer v) { return v == null ? 0 : v; }

    private String mapPosition(String pos) {
        if (pos == null) return "";
        return switch (pos.toUpperCase()) {
            case "G", "GK", "GOALKEEPER" -> "Goalkeeper";
            case "D", "DF", "DEFENDER" -> "Defender";
            case "M", "MF", "MIDFIELDER" -> "Midfielder";
            case "F", "FW", "FORWARD", "ATTACKER", "STRIKER" -> "Attacker";
            default -> pos;
        };
    }

    private String mapPositionCn(String pos) {
        if (pos == null) return "";
        return switch (pos.toUpperCase()) {
            case "G", "GK", "GOALKEEPER" -> "门将";
            case "D", "DF", "DEFENDER" -> "后卫";
            case "M", "MF", "MIDFIELDER" -> "中场";
            case "F", "FW", "FORWARD", "ATTACKER", "STRIKER" -> "前锋";
            default -> pos;
        };
    }
}
