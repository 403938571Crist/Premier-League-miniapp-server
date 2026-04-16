package com.premierleague.server.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 资讯实体 - JPA Entity
 * 增加抓取相关字段：fingerprint, sourcePublishedAt, fetchedAt, updatedAt
 */
@Entity
@Table(name = "news", indexes = {
    @Index(name = "idx_source_type", columnList = "sourceType"),
    @Index(name = "idx_media_type", columnList = "mediaType"),
    @Index(name = "idx_published_at", columnList = "sourcePublishedAt"),
    @Index(name = "idx_hot_score", columnList = "hotScore"),
    @Index(name = "idx_fingerprint", columnList = "fingerprint", unique = true),
    @Index(name = "idx_fetched_at", columnList = "fetchedAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class News {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @Column(nullable = false, length = 500)
    private String title;
    
    @Column(nullable = false, length = 2000)
    private String summary;
    
    @Column(nullable = false, length = 100)
    private String source;
    
    @Column(nullable = false, length = 32)
    private String sourceType;
    
    @Column(nullable = false, length = 32)
    private String mediaType;
    
    /**
     * 源站发布时间（业务时间）
     */
    @Column(nullable = false)
    private LocalDateTime sourcePublishedAt;
    
    @Column(length = 100)
    private String author;
    
    @Column(length = 500)
    private String coverImage;
    
    @Column(length = 500)
    private String tags;
    
    @Column(length = 500)
    private String relatedTeamIds;
    
    @Column(length = 500)
    private String relatedPlayerIds;
    
    private Integer hotScore;
    
    @Column(length = 500)
    private String url;
    
    @Column(length = 200)
    private String sourceNote;
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    // ========== 抓取相关字段 ==========
    
    /**
     * 去重指纹：MD5(title + sourceType + sourcePublishedAt)
     */
    @Column(length = 64, unique = true)
    private String fingerprint;
    
    /**
     * 首次抓取时间
     */
    private LocalDateTime fetchedAt;
    
    /**
     * 最后更新时间（内容有变化时更新）
     */
    private LocalDateTime contentUpdatedAt;
    
    /**
     * 抓取批次号
     */
    @Column(length = 32)
    private String fetchBatchId;
    
    /**
     * 抓取状态：pending / success / failed
     */
    @Column(length = 20)
    @Builder.Default
    private String fetchStatus = "success";
    
    /**
     * 抓取失败原因
     */
    @Column(length = 500)
    private String fetchError;
    
    // ========== 系统字段 ==========
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    // ========== 辅助方法 ==========
    
    public String[] getTagArray() {
        return tags != null ? tags.split(",") : new String[0];
    }
    
    public Long[] getRelatedTeamIdArray() {
        return relatedTeamIds != null 
            ? java.util.Arrays.stream(relatedTeamIds.split(","))
                .map(Long::parseLong)
                .toArray(Long[]::new)
            : new Long[0];
    }
    
    public Long[] getRelatedPlayerIdArray() {
        return relatedPlayerIds != null 
            ? java.util.Arrays.stream(relatedPlayerIds.split(","))
                .map(Long::parseLong)
                .toArray(Long[]::new)
            : new Long[0];
    }
}
