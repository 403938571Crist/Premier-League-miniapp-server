package com.premierleague.server.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 球员实体
 */
@Entity
@Table(name = "players", indexes = {
    @Index(name = "idx_team_id", columnList = "teamId"),
    @Index(name = "idx_position", columnList = "position"),
    @Index(name = "idx_api_id", columnList = "apiId", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Player {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 外部API ID
     */
    @Column(name = "api_id")
    private Long apiId;
    
    /**
     * 所属球队ID
     */
    @Column(name = "team_id")
    private Long teamId;
    
    /**
     * 姓名
     */
    @Column(nullable = false, length = 100)
    private String name;
    
    /**
     * 中文名
     */
    @Column(length = 50)
    private String chineseName;
    
    /**
     * 头像URL
     */
    @Column(length = 500)
    private String photoUrl;
    
    /**
     * 球衣号码
     */
    @Column(length = 10)
    private String shirtNumber;
    
    /**
     * 位置
     * Goalkeeper, Defender, Midfielder, Attacker
     */
    @Column(length = 20)
    private String position;
    
    /**
     * 中文位置
     */
    @Column(length = 20)
    private String chinesePosition;
    
    /**
     * 国籍
     */
    @Column(length = 50)
    private String nationality;
    
    /**
     * 出生日期
     */
    private LocalDate dateOfBirth;
    
    /**
     * 年龄（计算字段）
     */
    @Transient
    private Integer age;
    
    /**
     * 身高(cm)
     */
    private Integer height;
    
    /**
     * 体重(kg)
     */
    private Integer weight;
    
    /**
     * 惯用脚
     * Left, Right
     */
    @Column(length = 10)
    private String foot;
    
    /**
     * 合同到期日
     */
    private LocalDate contractUntil;
    
    /**
     * 市场价值(欧元)
     */
    private Long marketValue;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    /**
     * 计算年龄
     */
    public Integer getAge() {
        if (dateOfBirth == null) return null;
        return LocalDate.now().getYear() - dateOfBirth.getYear();
    }
}
