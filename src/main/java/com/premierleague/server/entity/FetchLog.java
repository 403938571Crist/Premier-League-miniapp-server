package com.premierleague.server.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 抓取日志实体
 * 记录每次抓取任务的执行情况
 */
@Entity
@Table(name = "fetch_log", indexes = {
    @Index(name = "idx_source_type", columnList = "sourceType"),
    @Index(name = "idx_batch_id", columnList = "batchId"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FetchLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 抓取批次号：yyyyMMddHHmmss_{sourceType}
     */
    @Column(length = 50, nullable = false)
    private String batchId;
    
    /**
     * 来源类型
     */
    @Column(length = 32, nullable = false)
    private String sourceType;
    
    /**
     * 抓取频率级别：high / medium / low
     */
    @Column(length = 20)
    private String frequencyLevel;
    
    /**
     * 状态：running / success / failed
     */
    @Column(length = 20)
    @Builder.Default
    private String status = "running";
    
    /**
     * 开始时间
     */
    private LocalDateTime startedAt;
    
    /**
     * 结束时间
     */
    private LocalDateTime endedAt;
    
    /**
     * 抓取耗时（毫秒）
     */
    private Long durationMs;
    
    /**
     * 请求数量
     */
    private Integer requestCount;
    
    /**
     * 新数据条数
     */
    private Integer newCount;
    
    /**
     * 更新数据条数
     */
    private Integer updatedCount;
    
    /**
     * 重复数据条数（去重）
     */
    private Integer duplicateCount;
    
    /**
     * 失败条数
     */
    private Integer failedCount;
    
    /**
     * 错误信息
     */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    // 方便方法
    public void complete() {
        this.endedAt = LocalDateTime.now();
        this.durationMs = java.time.Duration.between(startedAt, endedAt).toMillis();
        this.status = "success";
    }
    
    public void fail(String error) {
        this.endedAt = LocalDateTime.now();
        this.durationMs = java.time.Duration.between(startedAt, endedAt).toMillis();
        this.status = "failed";
        this.errorMessage = error;
    }
}
