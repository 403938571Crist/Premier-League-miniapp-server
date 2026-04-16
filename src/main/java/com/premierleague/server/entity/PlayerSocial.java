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
 * 球员社媒实体 - JPA Entity
 */
@Entity
@Table(name = "player_social", indexes = {
    @Index(name = "idx_player_id", columnList = "playerId"),
    @Index(name = "idx_team_id", columnList = "teamId"),
    @Index(name = "idx_platform", columnList = "platform")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerSocial {
    
    @Id
    @Column(length = 64)
    private String id;
    
    private Long playerId;
    
    @Column(nullable = false, length = 100)
    private String playerName;
    
    private Long teamId;
    
    @Column(nullable = false, length = 100)
    private String teamName;
    
    @Column(nullable = false, length = 32)
    private String platform;
    
    @Column(nullable = false, length = 100)
    private String handle;
    
    @Column(nullable = false, length = 500)
    private String profileUrl;
    
    @Column(length = 500)
    private String avatar;
    
    private Boolean verified;
    
    @Column(length = 500)
    private String summary;
    
    private LocalDateTime lastActiveAt;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
