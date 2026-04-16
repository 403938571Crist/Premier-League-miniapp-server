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
 * 比赛/赛程实体
 */
@Entity
@Table(name = "matches", indexes = {
    @Index(name = "idx_match_date", columnList = "matchDate"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_matchday", columnList = "matchday"),
    @Index(name = "idx_home_team", columnList = "homeTeamId"),
    @Index(name = "idx_away_team", columnList = "awayTeamId"),
    @Index(name = "idx_api_id", columnList = "apiId", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Match {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 外部API ID
     */
    @Column(name = "api_id")
    private Long apiId;
    
    /**
     * 赛季
     */
    @Column(length = 20)
    private String season;
    
    /**
     * 轮次
     */
    private Integer matchday;
    
    /**
     * 比赛时间
     */
    private LocalDateTime matchDate;
    
    /**
     * 比赛状态
     * SCHEDULED, LIVE, IN_PLAY, PAUSED, FINISHED, POSTPONED, SUSPENDED, CANCELLED
     */
    @Column(length = 20)
    private String status;
    
    /**
     * 主队
     */
    @Column(name = "home_team_id")
    private Long homeTeamId;
    
    @Column(name = "home_team_name")
    private String homeTeamName;
    
    @Column(name = "home_team_chinese_name")
    private String homeTeamChineseName;
    
    @Column(name = "home_team_crest")
    private String homeTeamCrest;
    
    /**
     * 客队
     */
    @Column(name = "away_team_id")
    private Long awayTeamId;
    
    @Column(name = "away_team_name")
    private String awayTeamName;
    
    @Column(name = "away_team_chinese_name")
    private String awayTeamChineseName;
    
    @Column(name = "away_team_crest")
    private String awayTeamCrest;
    
    /**
     * 比分
     */
    private Integer homeScore;
    private Integer awayScore;
    
    /**
     * 半场比分
     */
    private Integer homeHalfScore;
    private Integer awayHalfScore;
    
    /**
     * 比赛时长（分钟）
     */
    private Integer duration;
    
    /**
     * 比赛阶段
     * REGULAR_TIME, EXTRA_TIME, PENALTIES
     */
    @Column(length = 20)
    private String stage;
    
    /**
     * 比赛场地
     */
    @Column(length = 100)
    private String venue;
    
    /**
     * 裁判
     */
    @Column(length = 100)
    private String referee;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
