package com.premierleague.server.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * SQL 持久化缓存表。
 *
 * 用途：替代纯内存 Caffeine 缓存里那些调用昂贵外部 API 得到的结果
 *   - 射手榜 / 助攻榜（Understat / Fbref / api-football 慢查询）
 *   - 积分榜（football-data.org 有速率限制）
 *
 * 优先级：
 *   L1  Caffeine（进程内，毫秒级，TTL 短）
 *   L2  api_cache（MySQL，毫秒~10ms，重启不丢，TTL 可设几小时）
 *   L3  实时 API 调用（秒级，有配额限制）
 *
 * cache_value 存 JSON 字符串，由 SqlCacheService 负责序列化/反序列化。
 */
@Entity
@Table(
    name = "api_cache",
    indexes = {
        @Index(name = "idx_api_cache_expires", columnList = "expiresAt"),
        @Index(name = "idx_api_cache_prefix",  columnList = "cacheKey")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiCache {

    /** 缓存键，格式建议：{domain}:{subkey}，如 "topScorers:20" */
    @Id
    @Column(name = "cache_key", length = 255, nullable = false)
    private String cacheKey;

    /** 序列化后的 JSON 内容 */
    @Column(name = "cache_value", columnDefinition = "LONGTEXT", nullable = false)
    private String cacheValue;

    /** 缓存写入时间 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 缓存过期时间，过期后视为 miss */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** 命中次数，用于观察热点 */
    @Column(name = "hit_count", nullable = false)
    @Builder.Default
    private int hitCount = 0;
}
