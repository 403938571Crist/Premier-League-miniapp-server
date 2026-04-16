package com.premierleague.server.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.premierleague.server.entity.Match;
import com.premierleague.server.entity.Player;
import com.premierleague.server.entity.Team;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Football-Data.org API 数据提供者
 * 负责从 football-data.org v4 API 获取结构化数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FootballDataProvider {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${football-data.api.base-url:https://api.football-data.org/v4}")
    private String baseUrl;

    @Value("${football-data.api.token:}")
    private String apiToken;

    @Value("${football-data.api.competition-code:PL}")
    private String competitionCode;

    @Value("${football-data.cache.enabled:true}")
    private boolean cacheEnabled;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    // 上游状态到内部状态映射
    private static final java.util.Map<String, String> STATUS_MAPPING = java.util.Map.of(
        "SCHEDULED", "NOT_STARTED",
        "TIMED", "NOT_STARTED",
        "IN_PLAY", "LIVE",
        "PAUSED", "LIVE",
        "FINISHED", "FINISHED",
        "POSTPONED", "POSTPONED",
        "SUSPENDED", "POSTPONED",
        "CANCELLED", "CANCELLED"
    );

    /**
     * 获取 WebClient 实例
     */
    private WebClient getWebClient() {
        return webClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .defaultHeader("X-Auth-Token", apiToken)
            .build();
    }

    /**
     * 从缓存获取数据
     */
    private String getFromCache(String cacheKey) {
        if (!cacheEnabled) return null;
        try {
            return redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            log.warn("[FootballDataProvider] Redis cache get failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 保存数据到缓存
     */
    private void saveToCache(String cacheKey, String data, long ttlSeconds) {
        if (!cacheEnabled) return;
        try {
            redisTemplate.opsForValue().set(cacheKey, data, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[FootballDataProvider] Redis cache save failed: {}", e.getMessage());
        }
    }

    /**
     * 获取请求速率限制信息
     */
    public RateLimitInfo getRateLimitInfo() {
        String cacheKey = "fd:rate_limit";
        String cached = getFromCache(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, RateLimitInfo.class);
            } catch (Exception e) {
                log.warn("Failed to parse rate limit info from cache");
            }
        }
        return RateLimitInfo.builder()
            .remaining(10)
            .limit(10)
            .build();
    }

    /**
     * 检查是否超过速率限制
     */
    private boolean isRateLimitExceeded() {
        RateLimitInfo info = getRateLimitInfo();
        return info.getRemaining() <= 0;
    }

    /**
     * 更新速率限制信息
     */
    private void updateRateLimitInfo(HttpHeaders headers) {
        try {
            String remaining = headers.getFirst("X-Requests-Available-Minute");
            String limit = headers.getFirst("X-Request-Limit-Minute");
            if (remaining == null) remaining = "10";
            if (limit == null) limit = "10";
            
            RateLimitInfo info = RateLimitInfo.builder()
                .remaining(Integer.parseInt(remaining))
                .limit(Integer.parseInt(limit))
                .timestamp(LocalDateTime.now())
                .build();
            
            saveToCache("fd:rate_limit", objectMapper.writeValueAsString(info), 60);
        } catch (Exception e) {
            log.warn("Failed to update rate limit info: {}", e.getMessage());
        }
    }

    // ==================== 比赛相关接口 ====================

    /**
     * 按日期获取比赛列表
     * GET /v4/competitions/PL/matches?dateFrom={date}&dateTo={date}
     */
    public List<Match> fetchMatchesByDate(LocalDate date) {
        String cacheKey = "fd:matches:date:" + date;
        String cached = getFromCache(cacheKey);
        
        if (cached != null) {
            log.info("[FootballDataProvider] Cache hit for matches on {}", date);
            return parseMatchesFromJson(cached);
        }

        if (isRateLimitExceeded()) {
            log.warn("[FootballDataProvider] Rate limit exceeded, returning empty list");
            return new ArrayList<>();
        }

        try {
            String dateStr = date.format(DATE_FORMATTER);
            log.info("[FootballDataProvider] Fetching matches for date: {}", dateStr);

            String response = getWebClient()
                .get()
                .uri(uriBuilder -> uriBuilder
                    .path("/competitions/{competition}/matches")
                    .queryParam("dateFrom", dateStr)
                    .queryParam("dateTo", dateStr)
                    .build(competitionCode))
                .retrieve()
                .toEntity(String.class)
                .doOnSuccess(entity -> updateRateLimitInfo(entity.getHeaders()))
                .block(Duration.ofSeconds(10))
                .getBody();

            // 缓存 60 秒（今日赛程实时性高）
            saveToCache(cacheKey, response, 60);
            
            return parseMatchesFromJson(response);
        } catch (WebClientResponseException e) {
            log.error("[FootballDataProvider] API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("[FootballDataProvider] Failed to fetch matches by date: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 按轮次获取比赛列表
     * GET /v4/competitions/PL/matches?matchday={matchday}
     */
    public List<Match> fetchMatchesByMatchday(Integer matchday) {
        String cacheKey = "fd:matches:matchday:" + matchday;
        String cached = getFromCache(cacheKey);
        
        if (cached != null) {
            log.info("[FootballDataProvider] Cache hit for matchday {}", matchday);
            return parseMatchesFromJson(cached);
        }

        if (isRateLimitExceeded()) {
            log.warn("[FootballDataProvider] Rate limit exceeded, returning empty list");
            return new ArrayList<>();
        }

        try {
            log.info("[FootballDataProvider] Fetching matches for matchday: {}", matchday);

            String response = getWebClient()
                .get()
                .uri(uriBuilder -> uriBuilder
                    .path("/competitions/{competition}/matches")
                    .queryParam("matchday", matchday)
                    .build(competitionCode))
                .retrieve()
                .toEntity(String.class)
                .doOnSuccess(entity -> updateRateLimitInfo(entity.getHeaders()))
                .block(Duration.ofSeconds(10))
                .getBody();

            // 缓存 5 分钟
            saveToCache(cacheKey, response, 300);
            
            return parseMatchesFromJson(response);
        } catch (WebClientResponseException e) {
            log.error("[FootballDataProvider] API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("[FootballDataProvider] Failed to fetch matches by matchday: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 获取比赛详情
     * GET /v4/matches/{id}
     */
    public Optional<Match> fetchMatchDetail(Long matchId) {
        String cacheKey = "fd:match:detail:" + matchId;
        String cached = getFromCache(cacheKey);
        
        if (cached != null) {
            log.info("[FootballDataProvider] Cache hit for match {}", matchId);
            return Optional.ofNullable(parseMatchFromJson(cached));
        }

        if (isRateLimitExceeded()) {
            log.warn("[FootballDataProvider] Rate limit exceeded");
            return Optional.empty();
        }

        try {
            log.info("[FootballDataProvider] Fetching match detail: {}", matchId);

            String response = getWebClient()
                .get()
                .uri("/matches/{matchId}", matchId)
                .retrieve()
                .toEntity(String.class)
                .doOnSuccess(entity -> updateRateLimitInfo(entity.getHeaders()))
                .block(Duration.ofSeconds(10))
                .getBody();

            // 缓存 60-120 秒
            saveToCache(cacheKey, response, 90);
            
            return Optional.ofNullable(parseMatchFromJson(response));
        } catch (WebClientResponseException e) {
            log.error("[FootballDataProvider] API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception e) {
            log.error("[FootballDataProvider] Failed to fetch match detail: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 获取球队赛程
     * GET /v4/teams/{id}/matches?dateFrom={from}&dateTo={to}
     */
    public List<Match> fetchTeamMatches(Long teamId, LocalDate from, LocalDate to) {
        String cacheKey = String.format("fd:team:%d:matches:%s:%s", teamId, from, to);
        String cached = getFromCache(cacheKey);
        
        if (cached != null) {
            return parseMatchesFromJson(cached);
        }

        if (isRateLimitExceeded()) {
            return new ArrayList<>();
        }

        try {
            String fromStr = from.format(DATE_FORMATTER);
            String toStr = to.format(DATE_FORMATTER);

            String response = getWebClient()
                .get()
                .uri(uriBuilder -> uriBuilder
                    .path("/teams/{teamId}/matches")
                    .queryParam("dateFrom", fromStr)
                    .queryParam("dateTo", toStr)
                    .build(teamId))
                .retrieve()
                .toEntity(String.class)
                .doOnSuccess(entity -> updateRateLimitInfo(entity.getHeaders()))
                .block(Duration.ofSeconds(10))
                .getBody();

            // 缓存 6 小时
            saveToCache(cacheKey, response, 21600);
            
            return parseMatchesFromJson(response);
        } catch (Exception e) {
            log.error("[FootballDataProvider] Failed to fetch team matches: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // ==================== 积分榜相关接口 ====================

    /**
     * 获取英超积分榜
     * GET /v4/competitions/PL/standings
     */
    public List<Team> fetchStandings() {
        String cacheKey = "fd:standings:" + competitionCode;
        String cached = getFromCache(cacheKey);
        
        if (cached != null) {
            log.info("[FootballDataProvider] Cache hit for standings");
            return parseStandingsFromJson(cached);
        }

        if (isRateLimitExceeded()) {
            log.warn("[FootballDataProvider] Rate limit exceeded, returning empty list");
            return new ArrayList<>();
        }

        try {
            log.info("[FootballDataProvider] Fetching standings");

            String response = getWebClient()
                .get()
                .uri(uriBuilder -> uriBuilder
                    .path("/competitions/{competition}/standings")
                    .build(competitionCode))
                .retrieve()
                .toEntity(String.class)
                .doOnSuccess(entity -> updateRateLimitInfo(entity.getHeaders()))
                .block(Duration.ofSeconds(10))
                .getBody();

            // 缓存 5 分钟
            saveToCache(cacheKey, response, 300);
            
            return parseStandingsFromJson(response);
        } catch (WebClientResponseException e) {
            log.error("[FootballDataProvider] API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("[FootballDataProvider] Failed to fetch standings: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // ==================== 球队相关接口 ====================

    /**
     * 获取球队详情
     * GET /v4/teams/{id}
     */
    public Optional<Team> fetchTeam(Long teamId) {
        String cacheKey = "fd:team:detail:" + teamId;
        String cached = getFromCache(cacheKey);
        
        if (cached != null) {
            log.info("[FootballDataProvider] Cache hit for team {}", teamId);
            return Optional.ofNullable(parseTeamFromJson(cached));
        }

        if (isRateLimitExceeded()) {
            log.warn("[FootballDataProvider] Rate limit exceeded");
            return Optional.empty();
        }

        try {
            log.info("[FootballDataProvider] Fetching team: {}", teamId);

            String response = getWebClient()
                .get()
                .uri("/teams/{teamId}", teamId)
                .retrieve()
                .toEntity(String.class)
                .doOnSuccess(entity -> updateRateLimitInfo(entity.getHeaders()))
                .block(Duration.ofSeconds(10))
                .getBody();

            // 缓存 6 小时
            saveToCache(cacheKey, response, 21600);
            
            return Optional.ofNullable(parseTeamFromJson(response));
        } catch (WebClientResponseException e) {
            log.error("[FootballDataProvider] API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception e) {
            log.error("[FootballDataProvider] Failed to fetch team: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 获取球队阵容
     * GET /v4/teams/{id}
     */
    public List<Player> fetchTeamSquad(Long teamId) {
        String cacheKey = "fd:team:squad:" + teamId;
        String cached = getFromCache(cacheKey);
        
        if (cached != null) {
            log.info("[FootballDataProvider] Cache hit for team squad {}", teamId);
            return parseSquadFromJson(cached);
        }

        if (isRateLimitExceeded()) {
            log.warn("[FootballDataProvider] Rate limit exceeded, returning empty list");
            return new ArrayList<>();
        }

        try {
            log.info("[FootballDataProvider] Fetching team squad: {}", teamId);

            String response = getWebClient()
                .get()
                .uri("/teams/{teamId}", teamId)
                .retrieve()
                .toEntity(String.class)
                .doOnSuccess(entity -> updateRateLimitInfo(entity.getHeaders()))
                .block(Duration.ofSeconds(10))
                .getBody();

            // 缓存 6 小时
            saveToCache(cacheKey, response, 21600);
            
            return parseSquadFromJson(response);
        } catch (WebClientResponseException e) {
            log.error("[FootballDataProvider] API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("[FootballDataProvider] Failed to fetch team squad: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // ==================== 球员相关接口 ====================

    /**
     * 获取球员详情
     * GET /v4/persons/{id}
     */
    public Optional<Player> fetchPlayer(Long playerId) {
        String cacheKey = "fd:player:detail:" + playerId;
        String cached = getFromCache(cacheKey);
        
        if (cached != null) {
            log.info("[FootballDataProvider] Cache hit for player {}", playerId);
            return Optional.ofNullable(parsePlayerFromJson(cached));
        }

        if (isRateLimitExceeded()) {
            log.warn("[FootballDataProvider] Rate limit exceeded");
            return Optional.empty();
        }

        try {
            log.info("[FootballDataProvider] Fetching player: {}", playerId);

            String response = getWebClient()
                .get()
                .uri("/persons/{playerId}", playerId)
                .retrieve()
                .toEntity(String.class)
                .doOnSuccess(entity -> updateRateLimitInfo(entity.getHeaders()))
                .block(Duration.ofSeconds(10))
                .getBody();

            // 缓存 12 小时
            saveToCache(cacheKey, response, 43200);
            
            return Optional.ofNullable(parsePlayerFromJson(response));
        } catch (WebClientResponseException e) {
            log.error("[FootballDataProvider] API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception e) {
            log.error("[FootballDataProvider] Failed to fetch player: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 获取球员最近比赛
     * GET /v4/persons/{id}/matches?limit={limit}
     */
    public List<Match> fetchPlayerMatches(Long playerId, int limit) {
        String cacheKey = String.format("fd:player:%d:matches:%d", playerId, limit);
        String cached = getFromCache(cacheKey);
        
        if (cached != null) {
            return parseMatchesFromJson(cached);
        }

        if (isRateLimitExceeded()) {
            return new ArrayList<>();
        }

        try {
            String response = getWebClient()
                .get()
                .uri(uriBuilder -> uriBuilder
                    .path("/persons/{playerId}/matches")
                    .queryParam("limit", limit)
                    .build(playerId))
                .retrieve()
                .toEntity(String.class)
                .doOnSuccess(entity -> updateRateLimitInfo(entity.getHeaders()))
                .block(Duration.ofSeconds(10))
                .getBody();

            // 缓存 1 小时
            saveToCache(cacheKey, response, 3600);
            
            return parseMatchesFromJson(response);
        } catch (Exception e) {
            log.error("[FootballDataProvider] Failed to fetch player matches: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // ==================== JSON 解析方法 ====================

    /**
     * 解析比赛列表 JSON
     */
    private List<Match> parseMatchesFromJson(String json) {
        List<Match> matches = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode matchesNode = root.path("matches");
            
            if (matchesNode.isArray()) {
                for (JsonNode matchNode : matchesNode) {
                    Match match = parseMatchNode(matchNode);
                    if (match != null) {
                        matches.add(match);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[FootballDataProvider] Failed to parse matches JSON: {}", e.getMessage());
        }
        return matches;
    }

    /**
     * 解析单个比赛 JSON
     */
    private Match parseMatchFromJson(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            return parseMatchNode(root);
        } catch (Exception e) {
            log.error("[FootballDataProvider] Failed to parse match JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析比赛节点
     */
    private Match parseMatchNode(JsonNode node) {
        try {
            Match match = new Match();
            
            // 基本信息
            match.setApiId(node.path("id").asLong());
            match.setSeason(node.path("season").path("id").asText());
            match.setMatchday(node.path("matchday").asInt());
            
            // 比赛时间
            String utcDate = node.path("utcDate").asText();
            if (!utcDate.isEmpty()) {
                match.setMatchDate(LocalDateTime.parse(utcDate, DATETIME_FORMATTER));
            }
            
            // 状态映射
            String apiStatus = node.path("status").asText();
            match.setStatus(STATUS_MAPPING.getOrDefault(apiStatus, apiStatus));
            
            // 主队
            JsonNode homeTeam = node.path("homeTeam");
            match.setHomeTeamId(homeTeam.path("id").asLong());
            match.setHomeTeamName(homeTeam.path("name").asText());
            match.setHomeTeamCrest(homeTeam.path("crest").asText());
            match.setHomeTeamChineseName(getChineseTeamName(homeTeam.path("shortName").asText()));
            
            // 客队
            JsonNode awayTeam = node.path("awayTeam");
            match.setAwayTeamId(awayTeam.path("id").asLong());
            match.setAwayTeamName(awayTeam.path("name").asText());
            match.setAwayTeamCrest(awayTeam.path("crest").asText());
            match.setAwayTeamChineseName(getChineseTeamName(awayTeam.path("shortName").asText()));
            
            // 比分
            JsonNode score = node.path("score");
            JsonNode fullTime = score.path("fullTime");
            match.setHomeScore(fullTime.path("home").isNull() ? null : fullTime.path("home").asInt());
            match.setAwayScore(fullTime.path("away").isNull() ? null : fullTime.path("away").asInt());
            
            JsonNode halfTime = score.path("halfTime");
            match.setHomeHalfScore(halfTime.path("home").isNull() ? null : halfTime.path("home").asInt());
            match.setAwayHalfScore(halfTime.path("away").isNull() ? null : halfTime.path("away").asInt());
            
            // 场地和裁判
            match.setVenue(node.path("venue").asText());
            match.setReferee(node.path("referees").isArray() && node.path("referees").size() > 0 
                ? node.path("referees").get(0).path("name").asText() 
                : null);
            
            return match;
        } catch (Exception e) {
            log.error("[FootballDataProvider] Failed to parse match node: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析积分榜 JSON
     */
    private List<Team> parseStandingsFromJson(String json) {
        List<Team> teams = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode standingsNode = root.path("standings");
            
            if (standingsNode.isArray()) {
                for (JsonNode standing : standingsNode) {
                    String type = standing.path("type").asText();
                    // 只解析总榜
                    if ("TOTAL".equals(type)) {
                        JsonNode table = standing.path("table");
                        if (table.isArray()) {
                            for (JsonNode row : table) {
                                Team team = parseStandingRow(row);
                                if (team != null) {
                                    teams.add(team);
                                }
                            }
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.error("[FootballDataProvider] Failed to parse standings JSON: {}", e.getMessage());
        }
        return teams;
    }

    /**
     * 解析积分榜行
     */
    private Team parseStandingRow(JsonNode row) {
        try {
            Team team = new Team();
            
            JsonNode teamNode = row.path("team");
            team.setApiId(teamNode.path("id").asLong());
            team.setName(teamNode.path("name").asText());
            team.setShortName(teamNode.path("shortName").asText());
            team.setCrestUrl(teamNode.path("crest").asText());
            team.setChineseName(getChineseTeamName(teamNode.path("shortName").asText()));
            
            // 排名数据
            team.setPosition(row.path("position").asInt());
            team.setPlayedGames(row.path("playedGames").asInt());
            team.setWon(row.path("won").asInt());
            team.setDraw(row.path("draw").asInt());
            team.setLost(row.path("lost").asInt());
            team.setPoints(row.path("points").asInt());
            team.setGoalsFor(row.path("goalsFor").asInt());
            team.setGoalsAgainst(row.path("goalsAgainst").asInt());
            team.setGoalDifference(row.path("goalDifference").asInt());
            
            return team;
        } catch (Exception e) {
            log.error("[FootballDataProvider] Failed to parse standing row: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析球队 JSON
     */
    private Team parseTeamFromJson(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            
            Team team = new Team();
            team.setApiId(node.path("id").asLong());
            team.setName(node.path("name").asText());
            team.setShortName(node.path("shortName").asText());
            team.setChineseName(getChineseTeamName(node.path("shortName").asText()));
            team.setCrestUrl(node.path("crest").asText());
            team.setVenue(node.path("venue").asText());
            team.setFounded(node.path("founded").asInt());
            team.setClubColors(node.path("clubColors").asText());
            team.setWebsite(node.path("website").asText());
            
            // 主教练
            JsonNode coach = node.path("coach");
            if (!coach.isMissingNode() && !coach.isNull()) {
                // 可以扩展 coach 字段
            }
            
            return team;
        } catch (Exception e) {
            log.error("[FootballDataProvider] Failed to parse team JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析阵容 JSON
     */
    private List<Player> parseSquadFromJson(String json) {
        List<Player> players = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode squadNode = root.path("squad");
            Long teamId = root.path("id").asLong();
            
            if (squadNode.isArray()) {
                for (JsonNode playerNode : squadNode) {
                    Player player = parsePlayerNode(playerNode, teamId);
                    if (player != null) {
                        players.add(player);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[FootballDataProvider] Failed to parse squad JSON: {}", e.getMessage());
        }
        return players;
    }

    /**
     * 解析球员 JSON
     */
    private Player parsePlayerFromJson(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return parsePlayerNode(node, null);
        } catch (Exception e) {
            log.error("[FootballDataProvider] Failed to parse player JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析球员节点
     */
    private Player parsePlayerNode(JsonNode node, Long teamId) {
        try {
            Player player = new Player();
            
            player.setApiId(node.path("id").asLong());
            player.setName(node.path("name").asText());
            player.setPosition(node.path("position").asText());
            player.setChinesePosition(getChinesePosition(player.getPosition()));
            player.setShirtNumber(node.path("shirtNumber").asText());
            player.setNationality(node.path("nationality").asText());
            
            // 出生日期
            String dob = node.path("dateOfBirth").asText();
            if (!dob.isEmpty()) {
                player.setDateOfBirth(LocalDate.parse(dob));
            }
            
            // 所属球队
            if (teamId != null) {
                player.setTeamId(teamId);
            } else {
                JsonNode currentTeam = node.path("currentTeam");
                if (!currentTeam.isMissingNode() && !currentTeam.isNull()) {
                    player.setTeamId(currentTeam.path("id").asLong());
                }
            }
            
            return player;
        } catch (Exception e) {
            log.error("[FootballDataProvider] Failed to parse player node: {}", e.getMessage());
            return null;
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取球队中文名
     */
    private String getChineseTeamName(String shortName) {
        // 英超球队中文名映射
        java.util.Map<String, String> nameMap = new java.util.HashMap<>();
        nameMap.put("Arsenal", "阿森纳");
        nameMap.put("Aston Villa", "阿斯顿维拉");
        nameMap.put("Brentford", "布伦特福德");
        nameMap.put("Brighton", "布莱顿");
        nameMap.put("Burnley", "伯恩利");
        nameMap.put("Chelsea", "切尔西");
        nameMap.put("Crystal Palace", "水晶宫");
        nameMap.put("Everton", "埃弗顿");
        nameMap.put("Fulham", "富勒姆");
        nameMap.put("Liverpool", "利物浦");
        nameMap.put("Man City", "曼城");
        nameMap.put("Man United", "曼联");
        nameMap.put("Newcastle", "纽卡斯尔联");
        nameMap.put("Nottingham Forest", "诺丁汉森林");
        nameMap.put("Tottenham", "热刺");
        nameMap.put("West Ham", "西汉姆联");
        nameMap.put("Wolves", "狼队");
        nameMap.put("Bournemouth", "伯恩茅斯");
        nameMap.put("Sheffield United", "谢菲尔德联");
        nameMap.put("Luton Town", "卢顿");
        
        return nameMap.getOrDefault(shortName, shortName);
    }

    /**
     * 获取位置中文名
     */
    private String getChinesePosition(String position) {
        return switch (position) {
            case "Goalkeeper" -> "门将";
            case "Defender" -> "后卫";
            case "Midfielder" -> "中场";
            case "Attacker" -> "前锋";
            default -> position;
        };
    }

    /**
     * 速率限制信息
     */
    @lombok.Data
    @lombok.Builder
    public static class RateLimitInfo {
        private int remaining;
        private int limit;
        private LocalDateTime timestamp;
    }
}
