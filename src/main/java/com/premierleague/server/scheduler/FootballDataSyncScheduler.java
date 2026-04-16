package com.premierleague.server.scheduler;

import com.premierleague.server.entity.Match;
import com.premierleague.server.entity.Player;
import com.premierleague.server.entity.SyncLog;
import com.premierleague.server.entity.Team;
import com.premierleague.server.provider.FootballDataProvider;
import com.premierleague.server.repository.MatchRepository;
import com.premierleague.server.repository.PlayerRepository;
import com.premierleague.server.repository.SyncLogRepository;
import com.premierleague.server.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Football-Data.org 数据同步调度器
 * 负责定期从 API 同步数据到本地数据库
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FootballDataSyncScheduler {

    private final FootballDataProvider footballDataProvider;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;
    private final SyncLogRepository syncLogRepository;

    @Value("${football-data.sync.enabled:true}")
    private boolean syncEnabled;

    @Value("${football-data.sync.standings.interval:300000}")  // 默认 5 分钟
    private long standingsInterval;

    @Value("${football-data.sync.teams.interval:43200000}")     // 默认 12 小时
    private long teamsInterval;

    @Value("${football-data.sync.squad.interval:21600000}")     // 默认 6 小时
    private long squadInterval;

    private LocalDateTime lastStandingsSync = null;
    private LocalDateTime lastTeamsSync = null;
    private LocalDateTime lastSquadSync = null;

    // ==================== 定时任务 ====================

    /**
     * 同步积分榜 - 每 5 分钟执行
     * 实际执行频率由 standingsInterval 控制
     */
    @Scheduled(fixedRate = 300000) // 5 分钟
    public void scheduledSyncStandings() {
        if (!syncEnabled) {
            return;
        }
        
        // 检查是否达到间隔时间
        if (lastStandingsSync != null && 
            System.currentTimeMillis() - lastStandingsSync.toInstant(java.time.ZoneOffset.UTC).toEpochMilli() < standingsInterval) {
            return;
        }
        
        log.info("[FootballDataSyncScheduler] Starting scheduled standings sync");
        syncStandings();
    }

    /**
     * 同步今日比赛 - 每 2 分钟执行
     */
    @Scheduled(fixedRate = 120000) // 2 分钟
    @Transactional
    public void scheduledSyncTodayMatches() {
        if (!syncEnabled) {
            return;
        }
        
        log.info("[FootballDataSyncScheduler] Starting today matches sync");
        syncMatchesByDate(LocalDate.now());
    }

    /**
     * 同步未来 7 天比赛 - 每 30 分钟执行
     */
    @Scheduled(fixedRate = 1800000) // 30 分钟
    @Transactional
    public void scheduledSyncUpcomingMatches() {
        if (!syncEnabled) {
            return;
        }
        
        log.info("[FootballDataSyncScheduler] Starting upcoming matches sync");
        
        // 同步未来 7 天的比赛
        for (int i = 1; i <= 7; i++) {
            syncMatchesByDate(LocalDate.now().plusDays(i));
        }
    }

    /**
     * 同步球队信息 - 每 12 小时执行
     */
    @Scheduled(fixedRate = 43200000) // 12 小时
    @Transactional
    public void scheduledSyncTeams() {
        if (!syncEnabled) {
            return;
        }
        
        // 检查是否达到间隔时间
        if (lastTeamsSync != null && 
            System.currentTimeMillis() - lastTeamsSync.toInstant(java.time.ZoneOffset.UTC).toEpochMilli() < teamsInterval) {
            return;
        }
        
        log.info("[FootballDataSyncScheduler] Starting scheduled teams sync");
        syncAllTeams();
    }

    /**
     * 同步球队阵容 - 每 6 小时执行
     */
    @Scheduled(fixedRate = 21600000) // 6 小时
    @Transactional
    public void scheduledSyncSquads() {
        if (!syncEnabled) {
            return;
        }
        
        // 检查是否达到间隔时间
        if (lastSquadSync != null && 
            System.currentTimeMillis() - lastSquadSync.toInstant(java.time.ZoneOffset.UTC).toEpochMilli() < squadInterval) {
            return;
        }
        
        log.info("[FootballDataSyncScheduler] Starting scheduled squads sync");
        syncAllSquads();
    }

    // ==================== 同步方法 ====================

    /**
     * 同步积分榜
     */
    @Transactional
    public SyncLog syncStandings() {
        long startTime = System.currentTimeMillis();
        SyncLog.SyncLogBuilder logBuilder = SyncLog.builder()
            .syncType("STANDINGS")
            .syncTime(LocalDateTime.now())
            .source("football-data.org");

        try {
            log.info("[FootballDataSyncScheduler] Syncing standings from football-data.org");
            
            List<Team> teams = footballDataProvider.fetchStandings();
            
            if (teams.isEmpty()) {
                log.warn("[FootballDataSyncScheduler] No standings data fetched");
                logBuilder.status("FAILED")
                    .itemsCount(0)
                    .errorMessage("No data fetched from API");
                return saveSyncLog(logBuilder, startTime);
            }

            int successCount = 0;
            int failCount = 0;
            
            for (Team team : teams) {
                try {
                    // 检查是否已存在
                    Optional<Team> existing = teamRepository.findByApiId(team.getApiId());
                    if (existing.isPresent()) {
                        // 更新
                        Team existingTeam = existing.get();
                        existingTeam.setPosition(team.getPosition());
                        existingTeam.setPlayedGames(team.getPlayedGames());
                        existingTeam.setWon(team.getWon());
                        existingTeam.setDraw(team.getDraw());
                        existingTeam.setLost(team.getLost());
                        existingTeam.setPoints(team.getPoints());
                        existingTeam.setGoalsFor(team.getGoalsFor());
                        existingTeam.setGoalsAgainst(team.getGoalsAgainst());
                        existingTeam.setGoalDifference(team.getGoalDifference());
                        teamRepository.save(existingTeam);
                    } else {
                        // 新建
                        teamRepository.save(team);
                    }
                    successCount++;
                } catch (Exception e) {
                    log.error("[FootballDataSyncScheduler] Failed to save team {}: {}", team.getName(), e.getMessage());
                    failCount++;
                }
            }

            String status = failCount > 0 ? (successCount > 0 ? "PARTIAL" : "FAILED") : "SUCCESS";
            logBuilder.status(status)
                .itemsCount(teams.size())
                .successCount(successCount)
                .failCount(failCount)
                .detailLog(String.format("Synced %d teams, %d success, %d failed", teams.size(), successCount, failCount));
            
            lastStandingsSync = LocalDateTime.now();
            log.info("[FootballDataSyncScheduler] Standings sync completed: {} success, {} failed", successCount, failCount);
            
        } catch (Exception e) {
            log.error("[FootballDataSyncScheduler] Standings sync failed: {}", e.getMessage());
            logBuilder.status("FAILED")
                .errorMessage(e.getMessage());
        }

        return saveSyncLog(logBuilder, startTime);
    }

    /**
     * 按日期同步比赛
     */
    @Transactional
    public SyncLog syncMatchesByDate(LocalDate date) {
        long startTime = System.currentTimeMillis();
        SyncLog.SyncLogBuilder logBuilder = SyncLog.builder()
            .syncType("MATCHES")
            .syncTime(LocalDateTime.now())
            .source("football-data.org")
            .detailLog("Date: " + date.toString());

        try {
            log.info("[FootballDataSyncScheduler] Syncing matches for date: {}", date);
            
            List<Match> matches = footballDataProvider.fetchMatchesByDate(date);
            
            if (matches.isEmpty()) {
                logBuilder.status("SUCCESS")
                    .itemsCount(0)
                    .successCount(0)
                    .detailLog("No matches on " + date);
                return saveSyncLog(logBuilder, startTime);
            }

            int successCount = saveMatches(matches);
            int failCount = matches.size() - successCount;
            
            String status = failCount > 0 ? (successCount > 0 ? "PARTIAL" : "FAILED") : "SUCCESS";
            logBuilder.status(status)
                .itemsCount(matches.size())
                .successCount(successCount)
                .failCount(failCount);
            
            log.info("[FootballDataSyncScheduler] Matches sync completed for {}: {} success, {} failed", 
                date, successCount, failCount);
            
        } catch (Exception e) {
            log.error("[FootballDataSyncScheduler] Matches sync failed for {}: {}", date, e.getMessage());
            logBuilder.status("FAILED")
                .errorMessage(e.getMessage());
        }

        return saveSyncLog(logBuilder, startTime);
    }

    /**
     * 按轮次同步比赛
     */
    @Transactional
    public SyncLog syncMatchesByMatchday(Integer matchday) {
        long startTime = System.currentTimeMillis();
        SyncLog.SyncLogBuilder logBuilder = SyncLog.builder()
            .syncType("MATCHES")
            .syncTime(LocalDateTime.now())
            .source("football-data.org")
            .detailLog("Matchday: " + matchday);

        try {
            log.info("[FootballDataSyncScheduler] Syncing matches for matchday: {}", matchday);
            
            List<Match> matches = footballDataProvider.fetchMatchesByMatchday(matchday);
            
            if (matches.isEmpty()) {
                logBuilder.status("SUCCESS")
                    .itemsCount(0)
                    .successCount(0);
                return saveSyncLog(logBuilder, startTime);
            }

            int successCount = saveMatches(matches);
            int failCount = matches.size() - successCount;
            
            String status = failCount > 0 ? (successCount > 0 ? "PARTIAL" : "FAILED") : "SUCCESS";
            logBuilder.status(status)
                .itemsCount(matches.size())
                .successCount(successCount)
                .failCount(failCount);
            
        } catch (Exception e) {
            log.error("[FootballDataSyncScheduler] Matches sync failed for matchday {}: {}", matchday, e.getMessage());
            logBuilder.status("FAILED")
                .errorMessage(e.getMessage());
        }

        return saveSyncLog(logBuilder, startTime);
    }

    /**
     * 同步所有球队信息
     */
    @Transactional
    public SyncLog syncAllTeams() {
        long startTime = System.currentTimeMillis();
        SyncLog.SyncLogBuilder logBuilder = SyncLog.builder()
            .syncType("TEAMS")
            .syncTime(LocalDateTime.now())
            .source("football-data.org");

        try {
            // 先获取积分榜，包含所有球队
            List<Team> teams = footballDataProvider.fetchStandings();
            
            if (teams.isEmpty()) {
                log.warn("[FootballDataSyncScheduler] No teams data to sync");
                logBuilder.status("FAILED")
                    .errorMessage("No teams fetched");
                return saveSyncLog(logBuilder, startTime);
            }

            int successCount = 0;
            int failCount = 0;
            
            for (Team team : teams) {
                try {
                    // 获取详细信息
                    Optional<Team> detailOpt = footballDataProvider.fetchTeam(team.getApiId());
                    if (detailOpt.isPresent()) {
                        Team detail = detailOpt.get();
                        // 合并数据
                        team.setVenue(detail.getVenue());
                        team.setFounded(detail.getFounded());
                        team.setClubColors(detail.getClubColors());
                        team.setWebsite(detail.getWebsite());
                    }
                    
                    // 保存
                    Optional<Team> existing = teamRepository.findByApiId(team.getApiId());
                    if (existing.isPresent()) {
                        Team existingTeam = existing.get();
                        existingTeam.setName(team.getName());
                        existingTeam.setShortName(team.getShortName());
                        existingTeam.setCrestUrl(team.getCrestUrl());
                        existingTeam.setVenue(team.getVenue());
                        existingTeam.setFounded(team.getFounded());
                        existingTeam.setClubColors(team.getClubColors());
                        existingTeam.setWebsite(team.getWebsite());
                        existingTeam.setPosition(team.getPosition());
                        existingTeam.setPoints(team.getPoints());
                        // ... 更新其他字段
                        teamRepository.save(existingTeam);
                    } else {
                        teamRepository.save(team);
                    }
                    successCount++;
                    
                    // 速率限制：每分钟 10 请求，所以每 6 秒请求一次
                    Thread.sleep(6000);
                    
                } catch (Exception e) {
                    log.error("[FootballDataSyncScheduler] Failed to sync team {}: {}", team.getName(), e.getMessage());
                    failCount++;
                }
            }

            String status = failCount > 0 ? (successCount > 0 ? "PARTIAL" : "FAILED") : "SUCCESS";
            logBuilder.status(status)
                .itemsCount(teams.size())
                .successCount(successCount)
                .failCount(failCount);
            
            lastTeamsSync = LocalDateTime.now();
            log.info("[FootballDataSyncScheduler] Teams sync completed: {} success, {} failed", successCount, failCount);
            
        } catch (Exception e) {
            log.error("[FootballDataSyncScheduler] Teams sync failed: {}", e.getMessage());
            logBuilder.status("FAILED")
                .errorMessage(e.getMessage());
        }

        return saveSyncLog(logBuilder, startTime);
    }

    /**
     * 同步所有球队阵容
     */
    @Transactional
    public SyncLog syncAllSquads() {
        long startTime = System.currentTimeMillis();
        SyncLog.SyncLogBuilder logBuilder = SyncLog.builder()
            .syncType("SQUAD")
            .syncTime(LocalDateTime.now())
            .source("football-data.org");

        try {
            // 获取所有球队
            List<Team> teams = teamRepository.findAll();
            
            if (teams.isEmpty()) {
                log.warn("[FootballDataSyncScheduler] No teams in database to sync squads");
                logBuilder.status("FAILED")
                    .errorMessage("No teams in database");
                return saveSyncLog(logBuilder, startTime);
            }

            int totalPlayers = 0;
            int successCount = 0;
            int failCount = 0;
            
            for (Team team : teams) {
                try {
                    log.info("[FootballDataSyncScheduler] Syncing squad for team: {}", team.getName());
                    
                    List<Player> players = footballDataProvider.fetchTeamSquad(team.getApiId());
                    
                    if (!players.isEmpty()) {
                        // 设置球队 ID
                        players.forEach(p -> p.setTeamId(team.getId()));
                        
                        // 保存球员
                        int saved = savePlayers(players);
                        totalPlayers += saved;
                        successCount++;
                        
                        log.info("[FootballDataSyncScheduler] Saved {} players for {}", saved, team.getName());
                    }
                    
                    // 速率限制
                    Thread.sleep(6000);
                    
                } catch (Exception e) {
                    log.error("[FootballDataSyncScheduler] Failed to sync squad for {}: {}", team.getName(), e.getMessage());
                    failCount++;
                }
            }

            String status = failCount > 0 ? (successCount > 0 ? "PARTIAL" : "FAILED") : "SUCCESS";
            logBuilder.status(status)
                .itemsCount(totalPlayers)
                .successCount(successCount)
                .failCount(failCount)
                .detailLog(String.format("Synced squads for %d teams, %d total players", successCount, totalPlayers));
            
            lastSquadSync = LocalDateTime.now();
            log.info("[FootballDataSyncScheduler] Squad sync completed: {} teams, {} players", successCount, totalPlayers);
            
        } catch (Exception e) {
            log.error("[FootballDataSyncScheduler] Squad sync failed: {}", e.getMessage());
            logBuilder.status("FAILED")
                .errorMessage(e.getMessage());
        }

        return saveSyncLog(logBuilder, startTime);
    }

    // ==================== 辅助方法 ====================

    /**
     * 保存比赛列表
     */
    private int saveMatches(List<Match> matches) {
        int successCount = 0;
        
        for (Match match : matches) {
            try {
                Optional<Match> existing = matchRepository.findByApiId(match.getApiId());
                if (existing.isPresent()) {
                    // 更新
                    Match existingMatch = existing.get();
                    existingMatch.setStatus(match.getStatus());
                    existingMatch.setHomeScore(match.getHomeScore());
                    existingMatch.setAwayScore(match.getAwayScore());
                    existingMatch.setHomeHalfScore(match.getHomeHalfScore());
                    existingMatch.setAwayHalfScore(match.getAwayHalfScore());
                    existingMatch.setMatchDate(match.getMatchDate());
                    matchRepository.save(existingMatch);
                } else {
                    // 新建
                    matchRepository.save(match);
                }
                successCount++;
            } catch (Exception e) {
                log.error("[FootballDataSyncScheduler] Failed to save match {} vs {}: {}", 
                    match.getHomeTeamName(), match.getAwayTeamName(), e.getMessage());
            }
        }
        
        return successCount;
    }

    /**
     * 保存球员列表
     */
    private int savePlayers(List<Player> players) {
        int successCount = 0;
        
        for (Player player : players) {
            try {
                Optional<Player> existing = playerRepository.findByApiId(player.getApiId());
                if (existing.isPresent()) {
                    // 更新
                    Player existingPlayer = existing.get();
                    existingPlayer.setName(player.getName());
                    existingPlayer.setPosition(player.getPosition());
                    existingPlayer.setShirtNumber(player.getShirtNumber());
                    existingPlayer.setNationality(player.getNationality());
                    existingPlayer.setDateOfBirth(player.getDateOfBirth());
                    existingPlayer.setTeamId(player.getTeamId());
                    playerRepository.save(existingPlayer);
                } else {
                    // 新建
                    playerRepository.save(player);
                }
                successCount++;
            } catch (Exception e) {
                log.error("[FootballDataSyncScheduler] Failed to save player {}: {}", player.getName(), e.getMessage());
            }
        }
        
        return successCount;
    }

    /**
     * 保存同步日志
     */
    private SyncLog saveSyncLog(SyncLog.SyncLogBuilder builder, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        builder.durationMs(duration);
        
        SyncLog syncLog = builder.build();
        return syncLogRepository.save(syncLog);
    }
}
