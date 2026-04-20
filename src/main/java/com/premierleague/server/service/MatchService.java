package com.premierleague.server.service;

import com.premierleague.server.dto.PageResult;
import com.premierleague.server.entity.Match;
import com.premierleague.server.provider.FootballDataProvider;
import com.premierleague.server.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 比赛/赛程服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");
    private static final ZoneOffset STORAGE_ZONE = ZoneOffset.UTC;
    private static final int REFRESH_LOOKBACK_DAYS = 1;
    private static final int REFRESH_LOOKAHEAD_DAYS = 7;

    private final MatchRepository matchRepository;
    private final FootballDataProvider footballDataProvider;

    /**
     * 获取今日比赛
     * GET /api/matches/today
     * 
     * 先从数据库查询，如果没有则从 API 获取并保存
     */
    @Cacheable(value = "matchesToday", key = "'today'")
    public List<Match> getTodayMatches() {
        log.info("[MatchService] Getting today's matches");
        
        // 1. 先查数据库
        LocalDate today = LocalDate.now(APP_ZONE);
        refreshMatchesIfNeeded(today, today);
        List<Match> matches = matchRepository.findByMatchDateBetweenOrderByMatchDateAsc(
            toStorageStart(today),
            toStorageEnd(today)
        );
        
        // 2. 如果数据库没有，从 API 获取
        if (matches.isEmpty()) {
            log.info("[MatchService] No matches in DB, fetching from API");
            List<Match> apiMatches = footballDataProvider.fetchMatchesByDate(today);
            if (!apiMatches.isEmpty()) {
                // 保存到数据库
                matches = saveMatches(apiMatches);
            }
        }
        
        return matches;
    }

    /**
     * 获取某轮比赛
     * GET /api/matches?matchday=32
     */
    @Cacheable(value = "matchesByMatchday", key = "#matchday")
    public List<Match> getMatchesByMatchday(Integer matchday) {
        log.info("[MatchService] Getting matches for matchday {}", matchday);
        
        // 1. 先查数据库
        List<Match> matches = matchRepository.findByMatchdayOrderByMatchDateAsc(matchday);
        
        // 数据库无数据时直接返回空，不实时调外网
        if (matches.isEmpty()) {
            log.debug("[MatchService] No matches in DB for matchday {}, returning empty", matchday);
        }

        return matches;
    }

    /**
     * 按日期获取比赛
     * GET /api/matches?date=2026-04-12
     */
    @Cacheable(value = "matchesByDate", key = "#date.toString()")
    public List<Match> getMatchesByDate(LocalDate date) {
        log.info("[MatchService] Getting matches for date {}", date);

        refreshMatchesIfNeeded(date, date);

        LocalDateTime start = toStorageStart(date);
        LocalDateTime end = toStorageEnd(date);
        
        // 1. 先查数据库
        List<Match> matches = matchRepository.findByMatchDateBetweenOrderByMatchDateAsc(start, end);
        
        // 数据库无数据时直接返回空，不实时调外网（避免接口超时）
        // 比赛数据由定时任务定期同步
        if (matches.isEmpty()) {
            log.debug("[MatchService] No matches in DB for date {}, returning empty", date);
        }

        return matches;
    }

    /**
     * 获取当前轮次比赛
     */
    @Cacheable(value = "matchesCurrent", key = "'current'")
    public List<Match> getCurrentMatchday() {
        log.info("[MatchService] Getting current matchday");
        
        // 1. 先查数据库
        List<Match> matches = matchRepository.findCurrentMatchday();
        
        // 2. 如果没有，尝试获取最新轮次
        if (matches.isEmpty()) {
            Integer maxMatchday = matchRepository.findMaxMatchday();
            if (maxMatchday != null) {
                matches = getMatchesByMatchday(maxMatchday);
            }
        }
        
        return matches;
    }

    /**
     * 获取进行中的比赛
     */
    @Cacheable(value = "matchesLive", key = "'live'")
    public List<Match> getLiveMatches() {
        log.info("[MatchService] Getting live matches");
        
        List<String> liveStatuses = List.of("LIVE", "IN_PLAY", "PAUSED");
        List<Match> matches = matchRepository.findByStatusInOrderByMatchDateDesc(liveStatuses);
        
        // 如果数据库中没有，刷新今日比赛
        if (matches.isEmpty()) {
            List<Match> todayMatches = getTodayMatches();
            matches = todayMatches.stream()
                    .filter(m -> liveStatuses.contains(m.getStatus()))
                    .collect(Collectors.toList());
        }
        
        return matches;
    }

    /**
     * 获取所有赛程（分页）
     */
    @Cacheable(value = "matchesAll", key = "#page + '-' + #pageSize")
    public PageResult<Match> getAllMatches(int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        Page<Match> matches = matchRepository.findAll(pageable);
        
        return PageResult.of(
                matches.getContent(),
                page,
                pageSize,
                matches.getTotalElements()
        );
    }

    /**
     * 获取球队的比赛
     */
    @Cacheable(value = "matchesByTeam", key = "#teamId")
    public List<Match> getMatchesByTeam(Long teamId) {
        log.info("[MatchService] Getting matches for team {}", teamId);
        
        // 1. 先查数据库
        List<Match> matches = matchRepository.findByTeamId(teamId);
        
        // 2. 如果数据库中数据不足，从 API 获取
        if (matches.size() < 5) {
            log.info("[MatchService] Not enough matches in DB for team {}, fetching from API", teamId);
            LocalDate today = LocalDate.now(APP_ZONE);
            LocalDate from = today.minusMonths(3);
            LocalDate to = today.plusMonths(3);
            List<Match> apiMatches = footballDataProvider.fetchTeamMatches(teamId, from, to);
            if (!apiMatches.isEmpty()) {
                // 合并并去重
                saveMatches(apiMatches);
                matches = matchRepository.findByTeamId(teamId);
            }
        }
        
        return matches;
    }

    /**
     * 获取某日期范围内的比赛
     */
    @Cacheable(value = "matchesByDateRange", key = "#start + '-' + #end")
    public List<Match> getMatchesByDateRange(LocalDateTime start, LocalDateTime end) {
        refreshMatchesIfNeeded(start.toLocalDate(), end.toLocalDate());
        return matchRepository.findByMatchDateBetweenOrderByMatchDateAsc(
                toStorage(start),
                toStorage(end)
        );
    }

    /**
     * 获取比赛详情
     * 
     * 先查数据库，如果没有则从 API 获取并保存
     */
    @Cacheable(value = "matchDetail", key = "#id")
    public Match getMatchById(Long id) {
        log.info("[MatchService] Getting match detail: {}", id);
        
        // 1. 先查数据库
        Optional<Match> matchOpt = matchRepository.findById(id);
        if (matchOpt.isPresent()) {
            return matchOpt.get();
        }
        
        // 2. 尝试通过 API ID 查询
        Optional<Match> byApiId = matchRepository.findByApiId(id);
        if (byApiId.isPresent()) {
            return byApiId.get();
        }
        
        // 3. 从 API 获取
        log.info("[MatchService] Match {} not in DB, fetching from API", id);
        Optional<Match> apiMatch = footballDataProvider.fetchMatchDetail(id);
        if (apiMatch.isPresent()) {
            return matchRepository.save(apiMatch.get());
        }
        
        return null;
    }

    /**
     * 获取比赛详情（通过 API ID）
     */
    @Cacheable(value = "matchDetailByApiId", key = "#apiId")
    public Match getMatchByApiId(Long apiId) {
        log.info("[MatchService] Getting match by apiId: {}", apiId);
        
        // 1. 先查数据库
        Optional<Match> matchOpt = matchRepository.findByApiId(apiId);
        if (matchOpt.isPresent()) {
            return matchOpt.get();
        }
        
        // 2. 从 API 获取
        Optional<Match> apiMatch = footballDataProvider.fetchMatchDetail(apiId);
        if (apiMatch.isPresent()) {
            return matchRepository.save(apiMatch.get());
        }
        
        return null;
    }

    /**
     * 获取交锋记录
     */
    @Cacheable(value = "headToHead", key = "#team1 + '-' + #team2")
    public List<Match> getHeadToHead(Long team1, Long team2) {
        return matchRepository.findHeadToHead(team1, team2);
    }

    /**
     * 获取赛程日历数据（按日期分组）
     */
    public Map<String, List<Match>> getMatchesCalendar() {
        // 获取未来30天的比赛
        LocalDateTime start = LocalDateTime.now(APP_ZONE);
        LocalDateTime end = start.plusDays(30);
        
        List<Match> matches = matchRepository.findByMatchDateBetweenOrderByMatchDateAsc(start, end);
        
        // 按日期分组
        return matches.stream()
                .collect(Collectors.groupingBy(
                        m -> toAppTime(m.getMatchDate()).toLocalDate().toString()
                ));
    }

    /**
     * 保存或更新比赛
     */
    private void refreshMatchesIfNeeded(LocalDate requestedStart, LocalDate requestedEnd) {
        LocalDate today = LocalDate.now(APP_ZONE);
        LocalDate refreshStart = requestedStart.isBefore(today.minusDays(REFRESH_LOOKBACK_DAYS))
            ? today.minusDays(REFRESH_LOOKBACK_DAYS)
            : requestedStart;
        LocalDate refreshEnd = requestedEnd.isAfter(today.plusDays(REFRESH_LOOKAHEAD_DAYS))
            ? today.plusDays(REFRESH_LOOKAHEAD_DAYS)
            : requestedEnd;

        if (refreshStart.isAfter(refreshEnd)) {
            return;
        }

        LocalDate fetchStart = toStorageStart(refreshStart).toLocalDate();
        LocalDate fetchEnd = toStorageEnd(refreshEnd).toLocalDate();

        for (LocalDate date = fetchStart; !date.isAfter(fetchEnd); date = date.plusDays(1)) {
            refreshMatchesForDate(date);
        }
    }

    private LocalDateTime toStorageStart(LocalDate appDate) {
        return toStorage(appDate.atStartOfDay());
    }

    private LocalDateTime toStorageEnd(LocalDate appDate) {
        return toStorage(appDate.atTime(LocalTime.MAX));
    }

    private LocalDateTime toStorage(LocalDateTime appDateTime) {
        return appDateTime
                .atZone(APP_ZONE)
                .withZoneSameInstant(STORAGE_ZONE)
                .toLocalDateTime();
    }

    private LocalDateTime toAppTime(LocalDateTime storageDateTime) {
        return storageDateTime
                .atOffset(STORAGE_ZONE)
                .atZoneSameInstant(APP_ZONE)
                .toLocalDateTime();
    }

    private void refreshMatchesForDate(LocalDate date) {
        try {
            List<Match> apiMatches = footballDataProvider.fetchMatchesByDate(date);
            if (!apiMatches.isEmpty()) {
                saveMatches(apiMatches);
            }
        } catch (Exception e) {
            log.warn("[MatchService] Failed to refresh matches for {}: {}", date, e.getMessage());
        }
    }

    public Match saveMatch(Match match) {
        // 检查是否已存在
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
            existingMatch.setMatchday(match.getMatchday());
            existingMatch.setHomeTeamName(match.getHomeTeamName());
            existingMatch.setAwayTeamName(match.getAwayTeamName());
            existingMatch.setHomeTeamCrest(match.getHomeTeamCrest());
            existingMatch.setAwayTeamCrest(match.getAwayTeamCrest());
            return matchRepository.save(existingMatch);
        } else {
            // 新建
            return matchRepository.save(match);
        }
    }

    /**
     * 批量保存比赛
     */
    public List<Match> saveMatches(List<Match> matches) {
        List<Match> saved = new ArrayList<>();
        for (Match match : matches) {
            try {
                saved.add(saveMatch(match));
            } catch (Exception e) {
                log.error("[MatchService] Failed to save match {} vs {}: {}", 
                    match.getHomeTeamName(), match.getAwayTeamName(), e.getMessage());
            }
        }
        return saved;
    }
}
