package com.premierleague.server.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 数据同步日志
 * 记录从 football-data.org 等源同步数据的情况
 */
@Entity
@Table(name = "sync_logs", indexes = {
    @Index(name = "idx_sync_type", columnList = "syncType"),
    @Index(name = "idx_sync_time", columnList = "syncTime"),
    @Index(name = "idx_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 同步类型
     * STANDINGS - 积分榜
     * MATCHES - 赛程
     * TEAMS - 球队
     * PLAYERS - 球员
     * SQUAD - 阵容
     */
    @Column(name = "sync_type", length = 30, nullable = false)
    private String syncType;

    /**
     * 同步时间
     */
    @Column(name = "sync_time", nullable = false)
    private LocalDateTime syncTime;

    /**
     * 同步状态
     * SUCCESS - 成功
     * FAILED - 失败
     * PARTIAL - 部分成功
     */
    @Column(length = 20, nullable = false)
    private String status;

    /**
     * 同步数据量
     */
    @Column(name = "items_count")
    private Integer itemsCount;

    /**
     * 成功数量
     */
    @Column(name = "success_count")
    private Integer successCount;

    /**
     * 失败数量
     */
    @Column(name = "fail_count")
    private Integer failCount;

    /**
     * 数据源
     */
    @Column(length = 50)
    private String source;

    /**
     * 耗时（毫秒）
     */
    @Column(name = "duration_ms")
    private Long durationMs;

    /**
     * 错误信息
     */
    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    /**
     * 详细日志
     */
    @Column(name = "detail_log", length = 4000)
    private String detailLog;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
