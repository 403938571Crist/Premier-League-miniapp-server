package com.premierleague.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.premierleague.server.entity.Match;
import com.premierleague.server.entity.Player;
import com.premierleague.server.entity.Team;
import com.premierleague.server.model.PlayerStat;
import com.premierleague.server.provider.FootballDataProvider;
import com.premierleague.server.provider.PulseliveProvider;
import com.premierleague.server.repository.MatchRepository;
import com.premierleague.server.repository.PlayerRepository;
import com.premierleague.server.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 球队服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final MatchRepository matchRepository;
    private final FootballDataProvider footballDataProvider;
    private final PulseliveProvider pulseliveProvider;
    private final SqlCacheService sqlCache;

    private static final Duration SQL_CACHE_TTL_STANDINGS = Duration.ofMinutes(30);
    private static final TypeReference<List<Team>> TEAM_LIST = new TypeReference<>() {};

    /**
     * 获取积分榜
     * GET /api/teams/standings
     * 
     * 先查数据库，如果没有则从 API 获取并保存
     */
    @Cacheable(value = "standings", key = "'all'")
    public List<Team> getStandings() {
        log.info("[TeamService] Getting standings");

        // 1. DB is primary storage — if ≥20 teams, return directly
        List<Team> standings = teamRepository.findStandings();
        if (standings.size() >= 20) {
            return standings;
        }

        // 2. L2: SQL cache
        Optional<List<Team>> sqlHit = sqlCache.get("standings:all", TEAM_LIST);
        if (sqlHit.isPresent()) {
            log.info("[TeamService] standings SQL cache HIT");
            return sqlHit.get();
        }

        // 3. L3: real-time API (rate-limited)
        log.info("[TeamService] Not enough standings in DB ({} teams), fetching from API", standings.size());
        List<Team> apiStandings = footballDataProvider.fetchStandings();
        if (!apiStandings.isEmpty()) {
            standings = saveTeams(apiStandings);
            sqlCache.set("standings:all", standings, SQL_CACHE_TTL_STANDINGS);
        }

        return standings;
    }

    /**
     * 获取积分榜（按类型）
     * GET /api/teams/standings?type=TOTAL|HOME|AWAY
     * 
     * 注意：football-data.org 免费版只提供总榜，如需主场/客场榜需要额外处理
     */
    @Cacheable(value = "standings", key = "#type")
    public List<Team> getStandingsByType(String type) {
        log.info("[TeamService] Getting standings by type: {}", type);
        
        // 目前只支持 TOTAL，football-data.org 免费版提供 HOME 和 AWAY
        // 如果需要可以从 API 获取完整 standings 数据并按 type 分流
        return getStandings();
    }

    /**
     * 获取所有球队
     * GET /api/teams
     */
    @Cacheable(value = "teams", key = "'all'")
    public List<Team> getAllTeams() {
        List<Team> teams = teamRepository.findAll();
        
        // 如果数据库中没有球队，从 API 获取
        if (teams.isEmpty()) {
            log.info("[TeamService] No teams in DB, fetching from API");
            List<Team> apiTeams = footballDataProvider.fetchStandings();
            if (!apiTeams.isEmpty()) {
                teams = saveTeams(apiTeams);
            }
        }
        
        return teams;
    }

    /**
     * 获取球队详情
     * GET /api/teams/{id}
     * 
     * 先查数据库，如果没有则从 API 获取并保存
     */
    @Cacheable(value = "teamDetail", key = "#id")
    public Optional<Team> getTeamById(Long id) {
        log.info("[TeamService] Getting team: {}", id);
        
        // 1. 先查数据库
        Optional<Team> teamOpt = teamRepository.findById(id);
        if (teamOpt.isPresent()) {
            return teamOpt;
        }
        
        // 2. 尝试通过 API ID 查询
        Optional<Team> byApiId = teamRepository.findByApiId(id);
        if (byApiId.isPresent()) {
            return byApiId;
        }
        
        // 3. 从 API 获取
        log.info("[TeamService] Team {} not in DB, fetching from API", id);
        Optional<Team> apiTeam = footballDataProvider.fetchTeam(id);
        if (apiTeam.isPresent()) {
            Team team = teamRepository.save(apiTeam.get());
            return Optional.of(team);
        }
        
        return Optional.empty();
    }

    /**
     * 获取球队详情（通过 API ID）
     */
    @Cacheable(value = "teamDetailByApiId", key = "#apiId")
    public Optional<Team> getTeamByApiId(Long apiId) {
        log.info("[TeamService] Getting team by apiId: {}", apiId);
        
        // 1. 先查数据库
        Optional<Team> teamOpt = teamRepository.findByApiId(apiId);
        if (teamOpt.isPresent()) {
            return teamOpt;
        }
        
        // 2. 从 API 获取
        Optional<Team> apiTeam = footballDataProvider.fetchTeam(apiId);
        if (apiTeam.isPresent()) {
            Team team = teamRepository.save(apiTeam.get());
            return Optional.of(team);
        }
        
        return Optional.empty();
    }

    /**
     * 获取球队阵容
     * GET /api/teams/{id}/squad
     * 
     * 先查数据库，如果没有则从 API 获取并保存
     */
    @Cacheable(value = "teamSquad", key = "#teamId")
    public Map<String, Object> getTeamSquad(Long teamId) {
        log.info("[TeamService] Getting squad for team {}", teamId);
        
        Map<String, Object> squad = new HashMap<>();
        
        // 1. 先查数据库
        Optional<Team> teamOpt = getTeamById(teamId);
        List<Player> allPlayers = findPlayersForTeam(teamId, teamOpt);
        
        // 2. 如果数据库中没有，从 API 获取
        if (allPlayers.isEmpty() && teamOpt.isPresent()) {
            log.info("[TeamService] No squad in DB for team {}, fetching from API", teamId);
            
            // 获取球队 API ID
            if (teamOpt.get().getApiId() != null) {
                Long apiId = teamOpt.get().getApiId();
                Long storageTeamId = teamOpt.get().getId() != null ? teamOpt.get().getId() : teamId;
                List<Player> apiPlayers = footballDataProvider.fetchTeamSquad(apiId);
                if (!apiPlayers.isEmpty()) {
                    // 设置球队 ID
                    apiPlayers.forEach(p -> p.setTeamId(storageTeamId));
                    savePlayers(apiPlayers);
                    allPlayers = findPlayersForTeam(teamId, teamOpt);
                }
            }
        }
        
        // 按位置分组
        if (allPlayers.isEmpty() && teamOpt.isPresent()) {
            Long storageTeamId = teamOpt.get().getId() != null ? teamOpt.get().getId() : teamId;
            allPlayers = fetchSquadFromPulseliveLeaderboard(teamOpt.get(), storageTeamId);
        }

        List<Player> goalkeepers = playersInGroup(allPlayers, "Goalkeeper");
        List<Player> defenders = playersInGroup(allPlayers, "Defender");
        List<Player> midfielders = playersInGroup(allPlayers, "Midfielder");
        List<Player> attackers = playersInGroup(allPlayers, "Attacker");
        List<Player> others = allPlayers.stream()
                .filter(p -> positionGroup(p.getPosition()).isEmpty())
                .sorted(this::comparePlayers)
                .collect(Collectors.toList());
        
        squad.put("goalkeepers", goalkeepers);
        squad.put("defenders", defenders);
        squad.put("midfielders", midfielders);
        squad.put("attackers", attackers);
        squad.put("forwards", attackers);
        squad.put("others", others);
        squad.put("all", allPlayers);
        squad.put("totalCount", allPlayers.size());
        
        return squad;
    }

    private List<Player> findPlayersForTeam(Long requestedTeamId, Optional<Team> teamOpt) {
        Map<String, Player> merged = new LinkedHashMap<>();
        addPlayers(merged, playerRepository.findByTeamIdOrderByPositionAscShirtNumberAsc(requestedTeamId));
        if (teamOpt.isPresent()) {
            Team team = teamOpt.get();
            if (team.getId() != null && !team.getId().equals(requestedTeamId)) {
                addPlayers(merged, playerRepository.findByTeamIdOrderByPositionAscShirtNumberAsc(team.getId()));
            }
            if (team.getApiId() != null && !team.getApiId().equals(requestedTeamId)) {
                addPlayers(merged, playerRepository.findByTeamIdOrderByPositionAscShirtNumberAsc(team.getApiId()));
            }
        }
        return merged.values().stream()
                .sorted(this::comparePlayers)
                .collect(Collectors.toList());
    }

    private void addPlayers(Map<String, Player> merged, List<Player> players) {
        for (Player player : players) {
            merged.putIfAbsent(playerIdentity(player), player);
        }
    }

    private String playerIdentity(Player player) {
        if (player.getApiId() != null) {
            return "api:" + player.getApiId();
        }
        if (player.getId() != null) {
            return "db:" + player.getId();
        }
        return "name:" + player.getName();
    }

    private List<Player> fetchSquadFromPulseliveLeaderboard(Team team, Long storageTeamId) {
        try {
            List<PlayerStat> stats = pulseliveProvider.fetchScorers();
            if (stats == null || stats.isEmpty()) {
                return List.of();
            }
            return stats.stream()
                    .filter(stat -> isSameTeam(stat, team))
                    .map(stat -> toPlayer(stat, storageTeamId))
                    .filter(player -> player.getName() != null && !player.getName().isBlank())
                    .sorted(this::comparePlayers)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[TeamService] Pulselive squad fallback failed for {}: {}", team.getName(), e.getMessage());
            return List.of();
        }
    }

    private boolean isSameTeam(PlayerStat stat, Team team) {
        String teamKey = canonicalTeamKey(team.getName());
        String shortKey = canonicalTeamKey(team.getShortName());
        String statTeamKey = canonicalTeamKey(stat.teamName());
        String statShortKey = canonicalTeamKey(stat.teamShortName());

        return isMatchingTeamKey(teamKey, statTeamKey)
                || isMatchingTeamKey(teamKey, statShortKey)
                || isMatchingTeamKey(shortKey, statTeamKey)
                || isMatchingTeamKey(shortKey, statShortKey);
    }

    private boolean isMatchingTeamKey(String left, String right) {
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        if (left.equals(right)) {
            return true;
        }
        return left.length() > 6 && right.length() > 6 && (left.contains(right) || right.contains(left));
    }

    private String canonicalTeamKey(String value) {
        String key = normalizeKey(value);
        return switch (key) {
            case "manunited", "manutd" -> "manchesterunited";
            case "mancity" -> "manchestercity";
            case "spurs" -> "tottenhamhotspur";
            case "wolves" -> "wolverhamptonwanderers";
            case "nottmforest" -> "nottinghamforest";
            case "newcastle" -> "newcastleunited";
            case "westham" -> "westhamunited";
            case "brightonhovealbion" -> "brightonandhovealbion";
            default -> key;
        };
    }

    private String normalizeKey(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replace("&", "and")
                .replace("football club", "")
                .replace("afc", "")
                .replace("fc", "")
                .replaceAll("[^a-z0-9]", "");
    }

    private Player toPlayer(PlayerStat stat, Long teamId) {
        Player player = new Player();
        player.setApiId(stat.playerId());
        player.setTeamId(teamId);
        player.setName(stat.playerName());
        player.setChineseName(stat.chineseName());
        player.setNationality(stat.nationality());
        player.setPosition(stat.position());
        player.setChinesePosition(stat.chinesePosition());
        player.setShirtNumber(stat.shirtNumber() == null ? null : String.valueOf(stat.shirtNumber()));
        player.setPhotoUrl(stat.photoUrl());
        return player;
    }

    private List<Player> playersInGroup(List<Player> allPlayers, String group) {
        return allPlayers.stream()
                .filter(p -> group.equals(positionGroup(p.getPosition())))
                .sorted(this::comparePlayers)
                .collect(Collectors.toList());
    }

    private String positionGroup(String position) {
        if (position == null || position.isBlank()) {
            return "";
        }

        String value = position.toLowerCase(Locale.ROOT).replace('-', ' ').trim();
        if (value.equals("g") || value.equals("gk") || value.contains("goalkeeper") || value.contains("keeper")) {
            return "Goalkeeper";
        }
        if (value.equals("d") || value.equals("df") || value.equals("cb") || value.equals("lb")
                || value.equals("rb") || value.equals("lwb") || value.equals("rwb")
                || value.contains("defender") || value.contains("defence") || value.contains("defense")
                || value.contains("back") || value.contains("sweeper")) {
            return "Defender";
        }
        if (value.equals("m") || value.equals("mf") || value.equals("cm") || value.equals("dm")
                || value.equals("am") || value.equals("lm") || value.equals("rm")
                || value.contains("midfield")) {
            return "Midfielder";
        }
        if (value.equals("f") || value.equals("fw") || value.equals("cf") || value.equals("st")
                || value.equals("lw") || value.equals("rw")
                || value.contains("attacker") || value.contains("attack") || value.contains("forward")
                || value.contains("offence") || value.contains("offense")
                || value.contains("striker") || value.contains("winger")) {
            return "Attacker";
        }
        return "";
    }

    private int comparePlayers(Player left, Player right) {
        return Comparator
                .comparingInt((Player p) -> shirtNumberSortValue(p.getShirtNumber()))
                .thenComparing(Player::getName, Comparator.nullsLast(String::compareToIgnoreCase))
                .compare(left, right);
    }

    private int shirtNumberSortValue(String shirtNumber) {
        if (shirtNumber == null || shirtNumber.isBlank()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(shirtNumber.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * 获取球队最近比赛
     */
    @Cacheable(value = "teamMatches", key = "#teamId + '-recent'")
    public List<Match> getTeamRecentMatches(Long teamId) {
        return matchRepository.findLast5MatchesByTeamId(teamId);
    }

    /**
     * 获取球队统计信息
     */
    @Cacheable(value = "teamStats", key = "#teamId")
    public Map<String, Object> getTeamStats(Long teamId) {
        Optional<Team> teamOpt = getTeamById(teamId);
        if (teamOpt.isEmpty()) {
            return Map.of();
        }
        
        Team team = teamOpt.get();
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("played", team.getPlayedGames());
        stats.put("won", team.getWon());
        stats.put("draw", team.getDraw());
        stats.put("lost", team.getLost());
        stats.put("points", team.getPoints());
        stats.put("goalsFor", team.getGoalsFor());
        stats.put("goalsAgainst", team.getGoalsAgainst());
        stats.put("goalDifference", team.getGoalDifference());
        
        // 胜率
        if (team.getPlayedGames() > 0) {
            double winRate = (double) team.getWon() / team.getPlayedGames() * 100;
            stats.put("winRate", String.format("%.1f%%", winRate));
        }
        
        // 排名
        stats.put("position", team.getPosition());
        
        return stats;
    }

    /**
     * 保存或更新球队
     */
    public Team saveTeam(Team team) {
        // 检查是否已存在
        Optional<Team> existing = teamRepository.findByApiId(team.getApiId());
        if (existing.isPresent()) {
            // 更新
            Team existingTeam = existing.get();
            existingTeam.setName(team.getName());
            existingTeam.setShortName(team.getShortName());
            existingTeam.setChineseName(team.getChineseName());
            existingTeam.setCrestUrl(team.getCrestUrl());
            existingTeam.setVenue(team.getVenue());
            existingTeam.setFounded(team.getFounded());
            existingTeam.setClubColors(team.getClubColors());
            existingTeam.setWebsite(team.getWebsite());
            existingTeam.setPosition(team.getPosition());
            existingTeam.setPlayedGames(team.getPlayedGames());
            existingTeam.setWon(team.getWon());
            existingTeam.setDraw(team.getDraw());
            existingTeam.setLost(team.getLost());
            existingTeam.setPoints(team.getPoints());
            existingTeam.setGoalsFor(team.getGoalsFor());
            existingTeam.setGoalsAgainst(team.getGoalsAgainst());
            existingTeam.setGoalDifference(team.getGoalDifference());
            return teamRepository.save(existingTeam);
        } else {
            // 新建
            return teamRepository.save(team);
        }
    }

    /**
     * 批量保存球队
     */
    public List<Team> saveTeams(List<Team> teams) {
        List<Team> saved = new ArrayList<>();
        for (Team team : teams) {
            try {
                saved.add(saveTeam(team));
            } catch (Exception e) {
                log.error("[TeamService] Failed to save team {}: {}", team.getName(), e.getMessage());
            }
        }
        return saved;
    }

    /**
     * 保存或更新球员
     */
    public Player savePlayer(Player player) {
        // 检查是否已存在
        Optional<Player> existing = playerRepository.findByApiId(player.getApiId());
        if (existing.isPresent()) {
            // 更新
            Player existingPlayer = existing.get();
            existingPlayer.setName(player.getName());
            existingPlayer.setPosition(player.getPosition());
            existingPlayer.setChinesePosition(player.getChinesePosition());
            existingPlayer.setShirtNumber(player.getShirtNumber());
            existingPlayer.setNationality(player.getNationality());
            existingPlayer.setDateOfBirth(player.getDateOfBirth());
            existingPlayer.setTeamId(player.getTeamId());
            return playerRepository.save(existingPlayer);
        } else {
            // 新建
            return playerRepository.save(player);
        }
    }

    /**
     * 批量保存球员
     */
    public List<Player> savePlayers(List<Player> players) {
        List<Player> saved = new ArrayList<>();
        for (Player player : players) {
            try {
                saved.add(savePlayer(player));
            } catch (Exception e) {
                log.error("[TeamService] Failed to save player {}: {}", player.getName(), e.getMessage());
            }
        }
        return saved;
    }
}
