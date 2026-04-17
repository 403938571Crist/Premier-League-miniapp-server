package com.premierleague.server.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Caffeine 本地缓存配置
 * 替代 Redis，实现零 Redis 依赖的微信云托管部署
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(Arrays.asList(
            // ========== 资讯相关缓存 ==========
            buildCache("newsList", 5, TimeUnit.MINUTES),
            buildCache("newsDetail", 30, TimeUnit.MINUTES),
            buildCache("transferNews", 2, TimeUnit.MINUTES),
            buildCache("socialPlayers", 6, TimeUnit.HOURS),

            // ========== 积分榜/球队相关缓存 ==========
            buildCache("standings", 5, TimeUnit.MINUTES),
            buildCache("teams", 1, TimeUnit.HOURS),
            buildCache("teamDetail", 1, TimeUnit.HOURS),
            buildCache("teamDetailByApiId", 1, TimeUnit.HOURS),
            buildCache("teamSquad", 1, TimeUnit.HOURS),
            buildCache("teamMatches", 5, TimeUnit.MINUTES),
            buildCache("teamStats", 5, TimeUnit.MINUTES),
            buildCache("teamMostValuablePlayers", 1, TimeUnit.HOURS),

            // ========== 赛程相关缓存 ==========
            buildCache("matchesToday", 1, TimeUnit.MINUTES),
            buildCache("matchesByMatchday", 5, TimeUnit.MINUTES),
            buildCache("matchesByDate", 1, TimeUnit.MINUTES),
            buildCache("matchesCurrent", 1, TimeUnit.MINUTES),
            buildCache("matchesLive", 30, TimeUnit.SECONDS),
            buildCache("matchesAll", 5, TimeUnit.MINUTES),
            buildCache("matchesByTeam", 5, TimeUnit.MINUTES),
            buildCache("matchesByDateRange", 5, TimeUnit.MINUTES),
            buildCache("matchDetail", 30, TimeUnit.MINUTES),
            buildCache("matchDetailByApiId", 30, TimeUnit.MINUTES),
            buildCache("headToHead", 5, TimeUnit.MINUTES),

            // ========== 球员相关缓存 ==========
            buildCache("playerDetail", 12, TimeUnit.HOURS),
            buildCache("playerByApiId", 12, TimeUnit.HOURS),
            buildCache("playerMatches", 1, TimeUnit.HOURS),
            buildCache("playerSearch", 5, TimeUnit.MINUTES),

            // ========== FootballDataProvider 内部 API 缓存 ==========
            buildCache("fdMatchesDate", 1, TimeUnit.MINUTES),      // 60s
            buildCache("fdMatchesMatchday", 5, TimeUnit.MINUTES),  // 300s
            buildCache("fdMatchDetail", 90, TimeUnit.SECONDS),     // 90s
            buildCache("fdTeamMatches", 6, TimeUnit.HOURS),        // 21600s
            buildCache("fdStandings", 5, TimeUnit.MINUTES),        // 300s
            buildCache("fdTeamDetail", 6, TimeUnit.HOURS),         // 21600s
            buildCache("fdTeamSquad", 6, TimeUnit.HOURS),          // 21600s
            buildCache("fdPlayerDetail", 12, TimeUnit.HOURS),      // 43200s
            buildCache("fdPlayerMatches", 1, TimeUnit.HOURS),      // 3600s
            buildCache("fdScorers", 10, TimeUnit.MINUTES),         // 600s
            buildCache("fdFbrefScorers", 10, TimeUnit.MINUTES),    // 600s (fbref 备用数据源)
            buildCache("fdPulseliveScorers", 10, TimeUnit.MINUTES),// 600s (pulselive 官方备用)
            buildCache("fdUnderstatScorers", 10, TimeUnit.MINUTES),// 600s (understat 主备用)
            buildCache("fdRateLimit", 1, TimeUnit.MINUTES),        // 60s

            // ========== 射手榜/助攻榜服务层缓存 ==========
            buildCache("topScorers", 10, TimeUnit.MINUTES),
            buildCache("topAssists", 10, TimeUnit.MINUTES)
        ));
        return manager;
    }

    private CaffeineCache buildCache(String name, long duration, TimeUnit unit) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(duration, unit)
                .recordStats()
                .build());
    }
}
