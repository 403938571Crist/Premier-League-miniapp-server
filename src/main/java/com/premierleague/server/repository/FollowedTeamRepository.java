package com.premierleague.server.repository;

import com.premierleague.server.entity.FollowedTeam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface FollowedTeamRepository extends JpaRepository<FollowedTeam, Long> {

    List<FollowedTeam> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByUserIdAndTeamId(Long userId, Long teamId);

    @Transactional
    void deleteByUserIdAndTeamId(Long userId, Long teamId);
}
