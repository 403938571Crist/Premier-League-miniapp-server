package com.premierleague.server.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 缓存配置
 * 资讯 + 赛程/积分榜/球队 统一缓存管理
 */
@Configuration
@EnableCaching
public class RedisConfig {

    /**
     * 默认缓存配置 - 5分钟
     */
    @Bean
    public RedisCacheConfiguration defaultCacheConfig() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultCacheConfig())
                // 资讯相关缓存
                .withCacheConfiguration("newsList", newsListCacheConfig())
                .withCacheConfiguration("newsDetail", newsDetailCacheConfig())
                .withCacheConfiguration("transferNews", transferNewsCacheConfig())
                .withCacheConfiguration("socialPlayers", socialPlayersCacheConfig())
                // 赛程/积分榜/球队相关缓存
                .withCacheConfiguration("standings", standingsCacheConfig())
                .withCacheConfiguration("teams", teamsCacheConfig())
                .withCacheConfiguration("teamDetail", teamDetailCacheConfig())
                .withCacheConfiguration("teamSquad", teamSquadCacheConfig())
                .withCacheConfiguration("teamStats", teamStatsCacheConfig())
                .withCacheConfiguration("matchesToday", matchesTodayCacheConfig())
                .withCacheConfiguration("matchesLive", matchesLiveCacheConfig())
                .withCacheConfiguration("matchesByMatchday", matchesByMatchdayCacheConfig())
                .withCacheConfiguration("matchesCurrent", matchesCurrentCacheConfig())
                .withCacheConfiguration("matchesByTeam", matchesByTeamCacheConfig())
                .withCacheConfiguration("matchDetail", matchDetailCacheConfig())
                // 球员相关缓存
                .withCacheConfiguration("playerDetail", playerDetailCacheConfig())
                .withCacheConfiguration("playerMatches", playerMatchesCacheConfig())
                .withCacheConfiguration("playerSearch", playerSearchCacheConfig())
                .build();
    }

    // ========== 资讯相关缓存 ==========

    private RedisCacheConfiguration newsListCacheConfig() {
        return defaultCacheConfig().entryTtl(Duration.ofMinutes(5));
    }

    private RedisCacheConfiguration newsDetailCacheConfig() {
        return defaultCacheConfig().entryTtl(Duration.ofMinutes(30));
    }

    private RedisCacheConfiguration transferNewsCacheConfig() {
        return defaultCacheConfig().entryTtl(Duration.ofMinutes(2));
    }

    private RedisCacheConfiguration socialPlayersCacheConfig() {
        return defaultCacheConfig().entryTtl(Duration.ofHours(6));
    }

    // ========== 积分榜/球队相关缓存 ==========

    /**
     * 积分榜缓存 - 5分钟
     */
    private RedisCacheConfiguration standingsCacheConfig() {
        return defaultCacheConfig().entryTtl(Duration.ofMinutes(5));
    }

    /**
     * 球队列表缓存 - 1小时
     */
    private RedisCacheConfiguration teamsCacheConfig() {
        return defaultCacheConfig().entryTtl(Duration.ofHours(1));
    }

    /**
     * 球队详情缓存 - 1小时
     */
    private RedisCacheConfiguration teamDetailCacheConfig() {
        return defaultCacheConfig().entryTtl(Duration.ofHours(1));
    }

    /**
     * 球队阵容缓存 - 1小时
     */
    private RedisCacheConfiguration teamSquadCacheConfig() {
        return defaultCacheConfig().entryTtl(Duration.ofHours(1));
    }

    /**
     * 球队统计缓存 - 5分钟
     */
    private RedisCacheConfiguration teamStatsCacheConfig() {
        return defaultCacheConfig().entryTtl(Duration.ofMinutes(5));
    }

    // ========== 赛程相关缓存 ==========

    /**
     * 今日比赛缓存 - 1分钟（实时更新）
     */
    private RedisCacheConfiguration matchesTodayCacheConfig() {
        return defaultCacheConfig().entryTtl(Duration.ofMinutes(1));
    }

    /**
     * 进行中比赛缓存 - 30秒（实时比分）
     */
    private RedisCacheConfiguration matchesLiveCacheConfig() {
        return defaultCacheConfig().entryTtl(Duration.ofSeconds(30));
    }

    /**
     * 某轮比赛缓存 - 5分钟
     */
    private RedisCacheConfiguration matchesByMatchdayCacheConfig() {
        return defaultCacheConfig().entryTtl(Duration.ofMinutes(5));
    }

    /**
     * 当前轮次缓存 - 1分钟
     */
    private RedisCacheConfiguration matchesCurrentCacheConfig() {
        return defaultCacheConfig().entryTtl(Duration.ofMinutes(1));
    }

    /**
     * 球队比赛缓存 - 5分钟
     */
    private RedisCacheConfiguration matchesByTeamCacheConfig() {
        return defaultCacheConfig().entryTtl(Duration.ofMinutes(5));
    }

    /**
     * 比赛详情缓存 - 30分钟
     */
    private RedisCacheConfiguration matchDetailCacheConfig() {
        return defaultCacheConfig().entryTtl(Duration.ofMinutes(30));
    }

    // ========== 球员相关缓存 ==========

    /**
     * 球员详情缓存 - 12小时
     */
    private RedisCacheConfiguration playerDetailCacheConfig() {
        return defaultCacheConfig().entryTtl(Duration.ofHours(12));
    }

    /**
     * 球员比赛缓存 - 1小时
     */
    private RedisCacheConfiguration playerMatchesCacheConfig() {
        return defaultCacheConfig().entryTtl(Duration.ofHours(1));
    }

    /**
     * 球员搜索缓存 - 5分钟
     */
    private RedisCacheConfiguration playerSearchCacheConfig() {
        return defaultCacheConfig().entryTtl(Duration.ofMinutes(5));
    }
}
