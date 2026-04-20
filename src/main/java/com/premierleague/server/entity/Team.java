package com.premierleague.server.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 球队实体
 */
@Entity
@Table(name = "teams", indexes = {
    @Index(name = "idx_short_name", columnList = "shortName"),
    @Index(name = "idx_api_id", columnList = "apiId", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Team {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 外部API ID (football-data.org)
     */
    @Column(name = "api_id")
    private Long apiId;
    
    /**
     * 球队全称
     */
    @Column(nullable = false, length = 100)
    private String name;
    
    /**
     * 简称
     */
    @Column(length = 50)
    private String shortName;
    
    /**
     * 中文名称
     */
    @Column(length = 50)
    private String chineseName;
    
    /**
     * 队徽URL
     */
    @Column(length = 500)
    private String crestUrl;
    
    /**
     * 主场
     */
    @Column(length = 100)
    private String venue;
    
    /**
     * 成立年份
     */
    private Integer founded;
    
    /**
     * 俱乐部颜色
     */
    @Column(length = 50)
    private String clubColors;
    
    /**
     * 官方网站
     */
    @Column(length = 200)
    private String website;
    
    /**
     * 联赛排名相关信息
     */
    private Integer position;      // 排名
    private Integer playedGames;   // 已赛场次
    private Integer won;           // 胜
    private Integer draw;          // 平
    private Integer lost;          // 负
    private Integer points;        // 积分
    private Integer goalsFor;      // 进球
    private Integer goalsAgainst;  // 失球
    private Integer goalDifference; // 净胜球
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @JsonProperty("teamChineseName")
    public String getTeamChineseName() {
        return chineseName;
    }

    @JsonProperty("teamName")
    public String getTeamName() {
        return name;
    }

    @JsonProperty("teamId")
    public Long getTeamId() {
        return apiId;
    }

    @JsonProperty("crest")
    public String getCrest() {
        return crestUrl;
    }

    @JsonProperty("played")
    public Integer getPlayed() {
        return playedGames;
    }

    @JsonProperty("goals")
    public Integer getGoals() {
        return goalsFor;
    }
}
