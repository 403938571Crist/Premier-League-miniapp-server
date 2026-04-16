package com.premierleague.server.service;

import com.premierleague.server.entity.Match;
import com.premierleague.server.entity.Player;
import com.premierleague.server.entity.Team;
import com.premierleague.server.provider.FootballDataProvider;
import com.premierleague.server.repository.MatchRepository;
import com.premierleague.server.repository.PlayerRepository;
import com.premierleague.server.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    /**
     * 获取积分榜
     * GET /api/teams/standings
     * 
     * 先查数据库，如果没有则从 API 获取并保存
     */
    @Cacheable(value = "standings", key = "'all'")
    public List<Team> getStandings() {
        log.info("[TeamService] Getting standings");
        
        // 1. 先查数据库
        List<Team> standings = teamRepository.findStandings();
        
        // 2. 如果数据库中没有或数据不足 20 队，从 API 获取
        if (standings.size() < 20) {
            log.info("[TeamService] Not enough standings in DB ({} teams), fetching from API", standings.size());
            List<Team> apiStandings = footballDataProvider.fetchStandings();
            if (!apiStandings.isEmpty()) {
                standings = saveTeams(apiStandings);
            }
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
        List<Player> allPlayers = playerRepository.findByTeamIdOrderByPositionAscShirtNumberAsc(teamId);
        
        // 2. 如果数据库中没有，从 API 获取
        if (allPlayers.isEmpty()) {
            log.info("[TeamService] No squad in DB for team {}, fetching from API", teamId);
            
            // 获取球队 API ID
            Optional<Team> teamOpt = getTeamById(teamId);
            if (teamOpt.isPresent()) {
                Long apiId = teamOpt.get().getApiId();
                List<Player> apiPlayers = footballDataProvider.fetchTeamSquad(apiId);
                if (!apiPlayers.isEmpty()) {
                    // 设置球队 ID
                    apiPlayers.forEach(p -> p.setTeamId(teamId));
                    allPlayers = savePlayers(apiPlayers);
                }
            }
        }
        
        // 按位置分组
        List<Player> goalkeepers = allPlayers.stream()
                .filter(p -> "Goalkeeper".equals(p.getPosition()))
                .collect(Collectors.toList());
        List<Player> defenders = allPlayers.stream()
                .filter(p -> "Defender".equals(p.getPosition()))
                .collect(Collectors.toList());
        List<Player> midfielders = allPlayers.stream()
                .filter(p -> "Midfielder".equals(p.getPosition()))
                .collect(Collectors.toList());
        List<Player> attackers = allPlayers.stream()
                .filter(p -> "Attacker".equals(p.getPosition()))
                .collect(Collectors.toList());
        
        squad.put("goalkeepers", goalkeepers);
        squad.put("defenders", defenders);
        squad.put("midfielders", midfielders);
        squad.put("attackers", attackers);
        squad.put("all", allPlayers);
        squad.put("totalCount", allPlayers.size());
        
        return squad;
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
