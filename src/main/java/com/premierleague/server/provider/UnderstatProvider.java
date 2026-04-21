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

    @Cacheable(value = "fdUnderstatScorers", key = "'all'", sync = true)
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
                    mapPlayerToChinese(name),               // chineseName
                    null,                                   // nationality (understat 没有)
                    mapPosition(position),
                    mapPositionCn(position),
                    null,                                   // shirtNumber
                    null,                                   // teamId
                    teamTitle,
                    teamTitle,
                    mapTeamToChinese(teamTitle),
                    null,                                   // teamCrest
                    goals, assists, penalties, games,
                    null                                    // photoUrl - 由 PlayerService 在裁剪 top-N 后统一补全
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

    /** 球员英文名 → 中文（命中则返回中文，未命中返回 null，让前端直接显示英文） */
    private String mapPlayerToChinese(String name) {
        if (name == null || name.isEmpty()) return null;
        return PLAYER_ZH.get(name);
    }

    private static final Map<String, String> PLAYER_ZH = new HashMap<>();
    static {
        PLAYER_ZH.put("Eli Junior Kroupi", "克鲁皮");
        PLAYER_ZH.put("Junior Kroupi", "克鲁皮");
        PLAYER_ZH.put("Benjamin Šeško", "塞斯科");
        PLAYER_ZH.put("Benjamin Sesko", "塞斯科");
        PLAYER_ZH.put("Enzo Le Fée", "勒费");
        PLAYER_ZH.put("Enzo Le Fee", "勒费");
        PLAYER_ZH.put("Brenden Aaronson", "阿伦森");
        PLAYER_ZH.put("Jurriën Timber", "廷贝尔");
        PLAYER_ZH.put("Jurrien Timber", "廷贝尔");
        PLAYER_ZH.put("Mohammed Kudus", "库杜斯");
        PLAYER_ZH.put("Xavi Simons", "哈维·西蒙斯");
        PLAYER_ZH.put("Granit Xhaka", "扎卡");
        // 射手榜/助攻榜 热门球员（以本赛季数据为准）
        PLAYER_ZH.put("Erling Haaland", "哈兰德");
        PLAYER_ZH.put("Mohamed Salah", "萨拉赫");
        PLAYER_ZH.put("Bruno Fernandes", "B·费尔南德斯");
        PLAYER_ZH.put("Harry Kane", "凯恩");
        PLAYER_ZH.put("Bukayo Saka", "萨卡");
        PLAYER_ZH.put("Cole Palmer", "帕尔默");
        PLAYER_ZH.put("Phil Foden", "福登");
        PLAYER_ZH.put("Son Heung-Min", "孙兴慜");
        PLAYER_ZH.put("Alexander Isak", "伊萨克");
        PLAYER_ZH.put("Viktor Gyokeres", "约凯雷什");
        PLAYER_ZH.put("Viktor Gyökeres", "约凯雷什");
        PLAYER_ZH.put("Ollie Watkins", "沃特金斯");
        PLAYER_ZH.put("Dominic Solanke", "索兰克");
        PLAYER_ZH.put("Jarrod Bowen", "鲍文");
        PLAYER_ZH.put("Kai Havertz", "哈弗茨");
        PLAYER_ZH.put("Gabriel Jesus", "热苏斯");
        PLAYER_ZH.put("Gabriel Martinelli", "马丁内利");
        PLAYER_ZH.put("Martin Ødegaard", "厄德高");
        PLAYER_ZH.put("Martin Odegaard", "厄德高");
        PLAYER_ZH.put("Declan Rice", "赖斯");
        PLAYER_ZH.put("William Saliba", "萨利巴");
        PLAYER_ZH.put("Gabriel Magalhaes", "加布里埃尔");
        PLAYER_ZH.put("Virgil van Dijk", "范戴克");
        PLAYER_ZH.put("Trent Alexander-Arnold", "阿诺德");
        PLAYER_ZH.put("Luis Díaz", "路易斯·迪亚斯");
        PLAYER_ZH.put("Luis Diaz", "路易斯·迪亚斯");
        PLAYER_ZH.put("Darwin Núñez", "努涅斯");
        PLAYER_ZH.put("Darwin Nunez", "努涅斯");
        PLAYER_ZH.put("Hugo Ekitike", "埃基蒂克");
        PLAYER_ZH.put("Kevin De Bruyne", "德布劳内");
        PLAYER_ZH.put("Rodri", "罗德里");
        PLAYER_ZH.put("Bernardo Silva", "B·席尔瓦");
        PLAYER_ZH.put("Rúben Dias", "鲁本·迪亚斯");
        PLAYER_ZH.put("Ruben Dias", "鲁本·迪亚斯");
        PLAYER_ZH.put("Jérémy Doku", "多库");
        PLAYER_ZH.put("Jeremy Doku", "多库");
        PLAYER_ZH.put("Savinho", "萨维尼奥");
        PLAYER_ZH.put("Mathys Tel", "泰尔");
        PLAYER_ZH.put("Mathis Cherki", "谢尔基");
        PLAYER_ZH.put("Rayan Cherki", "谢尔基");
        PLAYER_ZH.put("Marcus Rashford", "拉什福德");
        PLAYER_ZH.put("Alejandro Garnacho", "加尔纳乔");
        PLAYER_ZH.put("Rasmus Højlund", "霍伊伦德");
        PLAYER_ZH.put("Rasmus Hojlund", "霍伊伦德");
        PLAYER_ZH.put("Casemiro", "卡塞米罗");
        PLAYER_ZH.put("Bryan Mbeumo", "姆伯莫");
        PLAYER_ZH.put("Nicolas Jackson", "尼古拉斯·杰克逊");
        PLAYER_ZH.put("Enzo Fernández", "恩佐·费尔南德斯");
        PLAYER_ZH.put("Enzo Fernandez", "恩佐·费尔南德斯");
        PLAYER_ZH.put("Moisés Caicedo", "凯塞多");
        PLAYER_ZH.put("Moises Caicedo", "凯塞多");
        PLAYER_ZH.put("João Pedro", "若昂·佩德罗");
        PLAYER_ZH.put("Joao Pedro", "若昂·佩德罗");
        PLAYER_ZH.put("Richarlison", "里夏利松");
        PLAYER_ZH.put("Dejan Kulusevski", "库卢塞夫斯基");
        PLAYER_ZH.put("Brennan Johnson", "布伦南·约翰逊");
        PLAYER_ZH.put("James Maddison", "麦迪逊");
        PLAYER_ZH.put("Cristian Romero", "罗梅罗");
        PLAYER_ZH.put("Guglielmo Vicario", "维卡里奥");
        PLAYER_ZH.put("Micky van de Ven", "范德文");
        PLAYER_ZH.put("Anthony Gordon", "戈登");
        PLAYER_ZH.put("Bruno Guimarães", "布鲁诺·吉马良斯");
        PLAYER_ZH.put("Bruno Guimaraes", "布鲁诺·吉马良斯");
        PLAYER_ZH.put("Sandro Tonali", "托纳利");
        PLAYER_ZH.put("Nick Pope", "波普");
        PLAYER_ZH.put("Thiago", "伊戈尔·蒂亚戈");     // Brentford 的 Igor Thiago（understat 里就叫 Thiago）
        PLAYER_ZH.put("Igor Thiago", "伊戈尔·蒂亚戈");
        PLAYER_ZH.put("Morgan Rogers", "摩根·罗杰斯");
        PLAYER_ZH.put("Morgan Gibbs-White", "吉布斯-怀特");
        PLAYER_ZH.put("Amad Diallo", "阿马德·迪亚洛");
        PLAYER_ZH.put("Dominik Szoboszlai", "索博斯洛伊");
        PLAYER_ZH.put("Alexis Mac Allister", "麦卡利斯特");
        PLAYER_ZH.put("Cody Gakpo", "加克波");
        PLAYER_ZH.put("Diogo Jota", "若塔");
        PLAYER_ZH.put("Ibrahima Konaté", "科纳特");
        PLAYER_ZH.put("Ibrahima Konate", "科纳特");
        PLAYER_ZH.put("Joshua Kimmich", "基米希");
        PLAYER_ZH.put("Mikel Merino", "梅里诺");
        PLAYER_ZH.put("Leandro Trossard", "特罗萨德");
        PLAYER_ZH.put("Ethan Nwaneri", "恩瓦内里");
        PLAYER_ZH.put("Myles Lewis-Skelly", "刘易斯-斯凯利");
        PLAYER_ZH.put("Antoine Semenyo", "塞门约");
        PLAYER_ZH.put("Danny Welbeck", "韦尔贝克");
        PLAYER_ZH.put("Kaoru Mitoma", "三笘薫");
        PLAYER_ZH.put("João Pedro", "若昂·佩德罗");
        PLAYER_ZH.put("Evan Ferguson", "弗格森");
        PLAYER_ZH.put("Jean-Philippe Mateta", "马特塔");
        PLAYER_ZH.put("Eberechi Eze", "埃泽");
        PLAYER_ZH.put("Marc Guéhi", "盖希");
        PLAYER_ZH.put("Marc Guehi", "盖希");
        PLAYER_ZH.put("Iliman Ndiaye", "恩迪亚耶");
        PLAYER_ZH.put("Jack Grealish", "格拉利什");
        PLAYER_ZH.put("James Tarkowski", "塔科夫斯基");
        PLAYER_ZH.put("James Garner", "加纳");
        PLAYER_ZH.put("Dominic Calvert-Lewin", "卡尔弗特-勒温");
        PLAYER_ZH.put("Harry Wilson", "哈里·威尔逊");
        PLAYER_ZH.put("Raúl Jiménez", "希门尼斯");
        PLAYER_ZH.put("Raul Jimenez", "希门尼斯");
        PLAYER_ZH.put("Alex Iwobi", "伊沃比");
        PLAYER_ZH.put("Andreas Pereira", "佩雷拉");
        PLAYER_ZH.put("Matheus Cunha", "库尼亚");
        PLAYER_ZH.put("Hwang Hee-Chan", "黄喜灿");
        PLAYER_ZH.put("Morgan Gibbs-White", "吉布斯-怀特");
        PLAYER_ZH.put("Chris Wood", "伍德");
        PLAYER_ZH.put("Callum Hudson-Odoi", "哈德森-奥多伊");
        PLAYER_ZH.put("Mbeumo Bryan", "姆伯莫");
        PLAYER_ZH.put("Yoane Wissa", "维萨");
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
