package com.premierleague.server.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "followed_teams", uniqueConstraints = {
        @UniqueConstraint(name = "uk_followed_team_user_team", columnNames = {"userId", "teamId"})
}, indexes = {
        @Index(name = "idx_followed_team_user_id", columnList = "userId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowedTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long teamId;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
