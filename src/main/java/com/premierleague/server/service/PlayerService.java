package com.premierleague.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.premierleague.server.entity.Match;
import com.premierleague.server.entity.Player;
import com.premierleague.server.model.PlayerStat;
import com.premierleague.server.exception.DataUnavailableException;
import com.premierleague.server.provider.ApiFootballProvider;
import com.premierleague.server.provider.FbrefProvider;
import com.premierleague.server.provider.FootballDataProvider;
import com.premierleague.server.provider.PlPhotoProvider;
import com.premierleague.server.provider.PulseliveProvider;
import com.premierleague.server.provider.UnderstatProvider;
import com.premierleague.server.repository.PlayerRepository;
import com.premierleague.server.util.PlayerChineseNameMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 球员服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final FootballDataProvider footballDataProvider;
    private final ApiFootballProvider apiFootballProvider;
    private final UnderstatProvider understatProvider;
    private final PulseliveProvider pulseliveProvider;
    private final FbrefProvider fbrefProvider;
    private final PlPhotoProvider plPhotoProvider;
    private final SqlCacheService sqlCache;

    private static final Duration SQL_CACHE_TTL_SCORERS = Duration.ofHours(2);
    private static final TypeReference<List<PlayerStat>> PLAYER_STAT_LIST =
            new TypeReference<>() {};
    private static final Map<String, String> PLAYER_ZH = Map.ofEntries(
            Map.entry("Eli Junior Kroupi", "克鲁皮"),
            Map.entry("Junior Kroupi", "克鲁皮"),
            Map.entry("Benjamin Šeško", "塞斯科"),
            Map.entry("Benjamin Sesko", "塞斯科"),
            Map.entry("Enzo Le Fée", "勒费"),
            Map.entry("Enzo Le Fee", "勒费"),
            Map.entry("Brenden Aaronson", "阿伦森"),
            Map.entry("Jurrien Timber", "廷贝尔"),
            Map.entry("Mohammed Kudus", "库杜斯"),
            Map.entry("Xavi Simons", "哈维·西蒙斯"),
            Map.entry("Granit Xhaka", "扎卡")
    );

    /**
     * 获取球员详情
     * GET /api/players/{id}
     * 
     * 先查数据库，没有则从 API 获取并保存
     */
    @Cacheable(value = "playerDetail", key = "#playerId", unless = "#result == null")
    public Optional<Player> getPlayerById(Long playerId) {
        log.info("[PlayerService] Getting player: {}", playerId);
        
        // 1. 先查数据库
        Optional<Player> playerOpt = playerRepository.findById(playerId)
                .or(() -> playerRepository.findByApiId(playerId));
        if (playerOpt.isPresent()) {
            return playerOpt;
        }
        
        // 2. 从 API 获取
        Optional<Player> apiPlayer = footballDataProvider.fetchPlayer(playerId);
        if (apiPlayer.isPresent()) {
            // 保存到数据库
            Player player = apiPlayer.get();
            player = playerRepository.save(player);
            return Optional.of(player);
        }
        
        return Optional.empty();
    }

    /**
     * 根据 API ID 获取球员
     */
    @Cacheable(value = "playerByApiId", key = "#apiId", unless = "#result == null")
    public Optional<Player> getPlayerByApiId(Long apiId) {
        log.info("[PlayerService] Getting player by apiId: {}", apiId);
        
        // 1. 先查数据库
        Optional<Player> playerOpt = playerRepository.findByApiId(apiId);
        if (playerOpt.isPresent()) {
            return playerOpt;
        }
        
        // 2. 从 API 获取
        Optional<Player> apiPlayer = footballDataProvider.fetchPlayer(apiId);
        if (apiPlayer.isPresent()) {
            Player player = apiPlayer.get();
            player = playerRepository.save(player);
            return Optional.of(player);
        }
        
        return Optional.empty();
    }

    /**
     * 获取球员最近比赛
     * GET /api/players/{id}/matches
     */
    @Cacheable(value = "playerMatches", key = "#playerId + '-' + #limit")
    public List<Match> getPlayerMatches(Long playerId, int limit) {
        log.info("[PlayerService] Getting matches for player: {}, limit: {}", playerId, limit);
        
        // 从 API 获取球员最近比赛
        return footballDataProvider.fetchPlayerMatches(playerId, limit);
    }

    /**
     * 获取球队阵容
     * 按位置分组返回
     */
    @Cacheable(value = "teamSquad", key = "#teamId")
    public List<Player> getTeamSquad(Long teamId) {
        log.info("[PlayerService] Getting squad for team: {}", teamId);
        
        // 1. 先查数据库
        List<Player> players = playerRepository.findByTeamIdOrderByPositionAscShirtNumberAsc(teamId);
        
        // 2. 如果数据库中没有，从 API 获取并保存
        if (players.isEmpty()) {
            List<Player> apiPlayers = footballDataProvider.fetchTeamSquad(teamId);
            if (!apiPlayers.isEmpty()) {
                // 批量保存
                players = playerRepository.saveAll(apiPlayers);
            }
        }
        
        return players;
    }

    /**
     * 搜索球员
     */
    @Cacheable(value = "playerSearch", key = "#keyword")
    public List<Player> searchPlayers(String keyword) {
        log.info("[PlayerService] Searching players with keyword: {}", keyword);
        return playerRepository.searchByName(keyword);
    }

    /**
     * 保存或更新球员
     */
    public Player savePlayer(Player player) {
        return playerRepository.save(player);
    }

    /**
     * 批量保存球员
     */
    public List<Player> saveAllPlayers(List<Player> players) {
        return playerRepository.saveAll(players);
    }

    /**
     * 获取身价最高的球员
     */
    @Cacheable(value = "teamMostValuablePlayers", key = "#teamId")
    public List<Player> getMostValuablePlayers(Long teamId) {
        return playerRepository.findMostValuablePlayersByTeamId(teamId);
    }

    /**
     * 射手榜 - 按进球数降序
     * GET /api/players/top-scorers
     *
     * 缓存层级：L1 Caffeine(10min) → L2 SQL(2h) → L3 实时 API
     */
    @Cacheable(value = "topScorers", key = "#limit")
    public List<PlayerStat> getTopScorers(int limit) {
        log.info("[PlayerService] Getting top scorers, limit={}", limit);

        // L2: SQL cache
        String cacheKey = "topScorers:" + limit;
        Optional<List<PlayerStat>> sqlHit = sqlCache.get(cacheKey, PLAYER_STAT_LIST);
        if (sqlHit.isPresent()) {
            log.info("[PlayerService] topScorers SQL cache HIT, limit={}", limit);
            return sqlHit.get();
        }

        // L3: 实时抓取
        List<PlayerStat> raw = fetchScorersWithFallback(limit);
        if (raw == null || raw.isEmpty()) {
            throw new DataUnavailableException("scorers",
                    "All upstream providers returned no scorer data");
        }

        List<PlayerStat> sorted = raw.stream()
                .sorted(Comparator
                        .comparingInt((PlayerStat s) -> s.goals() == null ? 0 : s.goals()).reversed()
                        .thenComparing(Comparator.comparingInt((PlayerStat s) -> s.assists() == null ? 0 : s.assists()).reversed())
                        .thenComparing(Comparator.comparingInt((PlayerStat s) -> s.playedMatches() == null ? Integer.MAX_VALUE : s.playedMatches())))
                .limit(limit)
                .toList();

        List<PlayerStat> result = assignRanks(sorted);
        sqlCache.set(cacheKey, result, SQL_CACHE_TTL_SCORERS);
        return result;
    }

    /**
     * 助攻榜 - 按助攻数降序
     * GET /api/players/top-assists
     *
     * 缓存层级：L1 Caffeine(10min) → L2 SQL(2h) → L3 实时 API
     */
    @Cacheable(value = "topAssists", key = "#limit")
    public List<PlayerStat> getTopAssists(int limit) {
        log.info("[PlayerService] Getting top assists, limit={}", limit);

        // L2: SQL cache
        String cacheKey = "topAssists:" + limit;
        Optional<List<PlayerStat>> sqlHit = sqlCache.get(cacheKey, PLAYER_STAT_LIST);
        if (sqlHit.isPresent()) {
            log.info("[PlayerService] topAssists SQL cache HIT, limit={}", limit);
            return sqlHit.get();
        }

        // L3: 实时抓取
        List<PlayerStat> raw = fetchAssistsWithFallback(Math.max(limit * 2, 40));
        if (raw == null || raw.isEmpty()) {
            throw new DataUnavailableException("assists",
                    "All upstream providers returned no assist data");
        }

        List<PlayerStat> sorted = raw.stream()
                .filter(s -> s.assists() != null && s.assists() > 0)
                .sorted(Comparator
                        .comparingInt((PlayerStat s) -> s.assists() == null ? 0 : s.assists()).reversed()
                        .thenComparing(Comparator.comparingInt((PlayerStat s) -> s.goals() == null ? 0 : s.goals()).reversed())
                        .thenComparing(Comparator.comparingInt((PlayerStat s) -> s.playedMatches() == null ? Integer.MAX_VALUE : s.playedMatches())))
                .limit(limit)
                .toList();

        List<PlayerStat> result = assignRanks(sorted);
        sqlCache.set(cacheKey, result, SQL_CACHE_TTL_SCORERS);
        return result;
    }

    /**
     * 射手数据源优先级（依次降级）：
     *   1. api-football /players/topscorers             （免费档 100 req/天，稳定 JSON，首选兜底）
     *   2. understat.com /main/getPlayersStats/         （国内直连无墙，JSON 结构最干净）
     *   3. pulselive v3 leaderboard                     （英超官方，dev 被 GFW DNS 污染）
     *   4. fbref.com Standard Stats                     （Cloudflare JS challenge，需 FlareSolverr）
     *   5. football-data.org /competitions/PL/scorers   （付费端点，当前环境常见 403/超时，最后兜底）
     *
     * 全部空时返回空列表；上游 getTopScorers 会抛 DataUnavailableException → 503
     */
    private List<PlayerStat> fetchScorersWithFallback(int limit) {
        List<PlayerStat> result;

        result = apiFootballProvider.fetchScorers();
        if (result != null && !result.isEmpty()) {
            log.info("[PlayerService] Using api-football scorers ({} rows)", result.size());
            return result;
        }
        log.info("[PlayerService] api-football unavailable, trying understat");

        result = understatProvider.fetchScorers();
        if (result != null && !result.isEmpty()) {
            log.info("[PlayerService] Using understat scorers ({} rows)", result.size());
            return result;
        }
        log.info("[PlayerService] understat unavailable, trying pulselive");

        result = pulseliveProvider.fetchScorers();
        if (result != null && !result.isEmpty()) {
            log.info("[PlayerService] Using pulselive scorers ({} rows)", result.size());
            return result;
        }
        log.info("[PlayerService] pulselive unavailable, falling back to fbref");

        result = fbrefProvider.fetchScorers();
        if (result != null && !result.isEmpty()) {
            log.info("[PlayerService] Using fbref scorers ({} rows)", result.size());
            return result;
        }

        log.info("[PlayerService] fbref unavailable, trying football-data as final fallback");

        result = footballDataProvider.fetchScorers(limit);
        if (result != null && !result.isEmpty()) {
            log.info("[PlayerService] Using football-data.org scorers ({} rows)", result.size());
            return result;
        }

        log.warn("[PlayerService] All scorer providers exhausted, returning empty");
        return new ArrayList<>();
    }

    /**
     * 助攻数据源优先级：
     *   1. api-football /players/topassists             （唯一正经的"按助攻"端点，直接返回排好序的数据）
     *   2. 其它源都没有独立助攻端点 —— 退回 scorers 链路，本地按 assists 重排
     */
    private List<PlayerStat> fetchAssistsWithFallback(int limit) {
        List<PlayerStat> direct = apiFootballProvider.fetchAssists();
        if (direct != null && !direct.isEmpty()) {
            log.info("[PlayerService] Using api-football assists ({} rows)", direct.size());
            return direct;
        }
        log.info("[PlayerService] api-football assists unavailable, reusing scorers chain");
        return fetchScorersWithFallback(limit);
    }

    /**
     * 给排好序的列表赋 rank（1-based）
     * 同分时共享同一排名（标准并列排名规则）
     */
    private List<PlayerStat> assignRanks(List<PlayerStat> sorted) {
        List<PlayerStat> result = new ArrayList<>(sorted.size());
        int displayRank = 0;
        Integer prevGoals = null;
        Integer prevAssists = null;
        for (int i = 0; i < sorted.size(); i++) {
            PlayerStat s = sorted.get(i);
            // 同 goals+assists 并列同名次；否则 rank = i+1
            boolean tieWithPrev = prevGoals != null
                    && prevGoals.equals(s.goals())
                    && prevAssists != null
                    && prevAssists.equals(s.assists());
            if (!tieWithPrev) {
                displayRank = i + 1;
            }
            String chineseName = s.chineseName();
            if (chineseName == null || chineseName.isBlank()) {
                chineseName = mapPlayerToChinese(s.playerName());
            }

            String photoUrl = findStoredPhotoUrl(s.playerName())
                    .orElseGet(() -> plPhotoProvider.findUsablePhotoUrl(s.playerName(), s.photoUrl()));

            result.add(new PlayerStat(
                    displayRank,
                    s.playerId(), s.playerName(), chineseName, s.nationality(),
                    s.position(), s.chinesePosition(), s.shirtNumber(),
                    s.teamId(), s.teamName(), s.teamShortName(), s.teamChineseName(), s.teamCrest(),
                    s.goals(), s.assists(), s.penalties(), s.playedMatches(),
                    photoUrl
            ));
            prevGoals = s.goals();
            prevAssists = s.assists();
        }
        return result;
    }

    private Optional<String> findStoredPhotoUrl(String playerName) {
        Optional<Player> exactMatch = playerRepository.findFirstWithPhotoByNameIgnoreCase(playerName);
        if (exactMatch.isPresent()) {
            return Optional.of(exactMatch.get().getPhotoUrl());
        }

        String normalized = stripAccents(playerName);
        if (!normalized.equals(playerName)) {
            return playerRepository.findFirstWithPhotoByNameIgnoreCase(normalized)
                    .map(Player::getPhotoUrl);
        }

        return Optional.empty();
    }

    private String mapPlayerToChinese(String playerName) {
        return PlayerChineseNameMapper.map(playerName);
    }

    private static String stripAccents(String s) {
        String nfd = Normalizer.normalize(s, Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{M}", "");
    }
}
