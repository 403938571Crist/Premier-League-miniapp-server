package com.premierleague.server.provider;

import com.premierleague.server.model.PlayerStat;
import com.premierleague.server.util.FlareSolverrClient;
import com.premierleague.server.util.HttpClientUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 备用数据源：fbref.com
 * 当 football-data.org 免费档拒绝 /scorers 时（403 FORBIDDEN），
 * 从 fbref 的 Premier League Standard Stats 页面抓取球员赛季数据。
 *
 * URL: https://fbref.com/en/comps/9/stats/Premier-League-Stats
 * 表格: table#stats_standard (球员维度)
 *
 * 说明：
 *   - fbref 页面刷新频率 ~分钟级，与 football-data 基本一致
 *   - 单次抓取返回联赛所有上场球员，service 层自行排序/截断
 *   - 不做 DB 持久化（纯只读聚合），10min 缓存
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FbrefProvider {

    private static final String FBREF_URL = "https://fbref.com/en/comps/9/stats/Premier-League-Stats";

    private final HttpClientUtil httpClient;
    private final FlareSolverrClient flareSolverr;

    /**
     * 抓取英超所有出场球员的赛季数据（含进球/助攻）
     * 返回未排序的 PlayerStat 列表（rank 为 null，由 service 层赋值）
     *
     * 抓取策略：
     *   - 优先走 FlareSolverr（若启用）—— 能过 Cloudflare JS challenge
     *   - 降级直连 —— fbref 大概率返回 "Just a moment..." 挑战页，解析出 0 行
     */
    @Cacheable(value = "fdFbrefScorers", key = "'all'")
    public List<PlayerStat> fetchScorers() {
        List<PlayerStat> stats = new ArrayList<>();
        try {
            String html = null;

            if (flareSolverr.isEnabled()) {
                log.info("[Fbref] Fetching scorers via FlareSolverr: {}", FBREF_URL);
                html = flareSolverr.get(FBREF_URL);
            }
            if (html == null || html.isEmpty()) {
                log.info("[Fbref] FlareSolverr unavailable/disabled, trying direct: {}", FBREF_URL);

                // fbref 对 UA 敏感，需要模拟浏览器
                Map<String, String> headers = new HashMap<>();
                headers.put("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
                headers.put("Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                headers.put("Accept-Language", "en-US,en;q=0.9");
                headers.put("Referer", "https://fbref.com/en/comps/9/Premier-League-Stats");

                html = httpClient.getWithHeaders(FBREF_URL, headers);
            }

            if (html == null || html.isEmpty()) {
                log.warn("[Fbref] Empty HTML, cannot parse scorers");
                return stats;
            }
            if (html.contains("Just a moment") || html.contains("challenge-platform")) {
                log.warn("[Fbref] Hit Cloudflare challenge page (no FlareSolverr?), giving up");
                return stats;
            }

            Document doc = Jsoup.parse(html);

            // fbref 有时把表格包在 HTML 注释里（反爬）。先尝试直接取；若找不到，再从注释里挖
            Element table = doc.selectFirst("table#stats_standard");
            if (table == null) {
                table = extractTableFromComments(doc, "stats_standard");
            }
            if (table == null) {
                log.warn("[Fbref] table#stats_standard not found on page");
                return stats;
            }

            Elements rows = table.select("tbody tr");
            log.info("[Fbref] Parsing {} player rows", rows.size());

            for (Element row : rows) {
                // 跳过分组表头（class="thead"）
                if (row.hasClass("thead")) continue;

                PlayerStat stat = parseRow(row);
                if (stat != null) stats.add(stat);
            }

            log.info("[Fbref] Parsed {} player stats", stats.size());
        } catch (Exception e) {
            log.error("[Fbref] Failed to fetch scorers: {}", e.getMessage(), e);
        }
        return stats;
    }

    /**
     * 解析单行球员数据。字段定位用 td[data-stat=xxx]，避免列顺序变化带来的断裂。
     */
    private PlayerStat parseRow(Element row) {
        try {
            String playerName = textOf(row, "player");
            if (playerName == null || playerName.isEmpty()) return null;

            Long playerId = parsePlayerIdFromHref(row);
            String nationality = parseNationality(row);
            String teamName = textOf(row, "team");
            String position = textOf(row, "position");
            Integer games = intOf(row, "games");
            Integer goals = intOf(row, "goals");
            Integer assists = intOf(row, "assists");
            Integer penalties = intOf(row, "pens_made");

            return new PlayerStat(
                    null,                           // rank (service 层赋值)
                    playerId,                       // playerId（fbref slug hash 转 long）
                    playerName,
                    null,                           // chineseName 暂无映射
                    nationality,
                    mapPositionToFd(position),      // 英文标准位置
                    mapPositionToChinese(position), // 中文位置
                    null,                           // shirtNumber 不在该表
                    null,                           // teamId 不在该表
                    teamName,
                    teamName,                       // shortName 用同值
                    mapTeamToChinese(teamName),
                    null,                           // teamCrest 不在该表（需要单独查询）
                    nz(goals),
                    nz(assists),
                    nz(penalties),
                    nz(games),
                    null                            // photoUrl - fbref 不提供
            );
        } catch (Exception e) {
            log.debug("[Fbref] Failed to parse row: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 有些版本的 fbref 会把表格藏在 <!-- ... --> 注释里。
     * 这里从整个 HTML 源码里搜 <table id="xxx">...</table>，用 Jsoup 再解析一次。
     */
    private Element extractTableFromComments(Document doc, String tableId) {
        String html = doc.html();
        String marker = "<table " + "class=\"stats_table\" id=\"" + tableId + "\"";
        int start = html.indexOf(marker);
        if (start < 0) {
            // 宽松匹配
            start = html.indexOf("id=\"" + tableId + "\"");
            if (start < 0) return null;
            // 回溯到 <table
            start = html.lastIndexOf("<table", start);
            if (start < 0) return null;
        }
        int end = html.indexOf("</table>", start);
        if (end < 0) return null;
        String tableHtml = html.substring(start, end + "</table>".length());
        Document fragment = Jsoup.parse(tableHtml);
        return fragment.selectFirst("table#" + tableId);
    }

    private String textOf(Element row, String dataStat) {
        Element cell = row.selectFirst("[data-stat=" + dataStat + "]");
        return cell == null ? null : cell.text().trim();
    }

    private Integer intOf(Element row, String dataStat) {
        String text = textOf(row, dataStat);
        if (text == null || text.isEmpty()) return null;
        try {
            // fbref 数字里偶尔有千分位逗号
            return Integer.parseInt(text.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从 player 链接 /en/players/{hash}/{slug} 里抽 hash，转为稳定 long id。
     * fbref 没有数字 id，这里取 hash 前 8 位 hex → long，仅用于前端 key 去重。
     */
    private Long parsePlayerIdFromHref(Element row) {
        Element a = row.selectFirst("td[data-stat=player] a, th[data-stat=player] a");
        if (a == null) return null;
        String href = a.attr("href"); // /en/players/98ea5115/Erling-Haaland
        String[] parts = href.split("/");
        if (parts.length < 4) return null;
        String hash = parts[3];
        if (hash.length() > 8) hash = hash.substring(0, 8);
        try {
            return Long.parseLong(hash, 16);
        } catch (NumberFormatException e) {
            return (long) href.hashCode();
        }
    }

    /**
     * 国籍单元格形如 "eng ENG" 或 "fr FRA" — 取最后的 3 字母国家码
     */
    private String parseNationality(Element row) {
        String text = textOf(row, "nationality");
        if (text == null || text.isEmpty()) return null;
        String[] parts = text.trim().split("\\s+");
        return parts[parts.length - 1];
    }

    private int nz(Integer v) {
        return v == null ? 0 : v;
    }

    /**
     * fbref 位置码 → football-data 风格（保持与现有 PlayerStat.position 字段一致）
     */
    private String mapPositionToFd(String fbrefPos) {
        if (fbrefPos == null || fbrefPos.isEmpty()) return "";
        // fbref 可能给 "FW,MF"，取主位置（第一段）
        String primary = fbrefPos.split(",")[0].trim();
        return switch (primary) {
            case "GK" -> "Goalkeeper";
            case "DF" -> "Defender";
            case "MF" -> "Midfielder";
            case "FW" -> "Attacker";
            default -> primary;
        };
    }

    private String mapPositionToChinese(String fbrefPos) {
        if (fbrefPos == null || fbrefPos.isEmpty()) return "";
        String primary = fbrefPos.split(",")[0].trim();
        return switch (primary) {
            case "GK" -> "门将";
            case "DF" -> "后卫";
            case "MF" -> "中场";
            case "FW" -> "前锋";
            default -> primary;
        };
    }

    /**
     * fbref 队名 → 中文名。fbref 的队名拼写与 football-data 的 shortName 略有差异
     * （如 "Manchester City" vs "Man City"），这里直接建立 fbref 名 → 中文 的映射。
     */
    private String mapTeamToChinese(String fbrefTeam) {
        if (fbrefTeam == null) return null;
        Map<String, String> map = TEAM_ZH;
        return map.getOrDefault(fbrefTeam, fbrefTeam);
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
        TEAM_ZH.put("Ipswich Town", "伊普斯维奇");
        TEAM_ZH.put("Leeds United", "利兹联");
        TEAM_ZH.put("Leicester City", "莱斯特城");
        TEAM_ZH.put("Liverpool", "利物浦");
        TEAM_ZH.put("Luton Town", "卢顿");
        TEAM_ZH.put("Manchester City", "曼城");
        TEAM_ZH.put("Manchester Utd", "曼联");
        TEAM_ZH.put("Newcastle Utd", "纽卡斯尔联");
        TEAM_ZH.put("Nott'ham Forest", "诺丁汉森林");
        TEAM_ZH.put("Sheffield Utd", "谢菲尔德联");
        TEAM_ZH.put("Southampton", "南安普顿");
        TEAM_ZH.put("Sunderland", "桑德兰");
        TEAM_ZH.put("Tottenham", "热刺");
        TEAM_ZH.put("West Ham", "西汉姆联");
        TEAM_ZH.put("Wolves", "狼队");
    }
}
