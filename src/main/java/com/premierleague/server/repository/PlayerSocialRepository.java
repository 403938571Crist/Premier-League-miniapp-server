package com.premierleague.server.repository;

import com.premierleague.server.entity.PlayerSocial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 球员社媒 Repository
 */
@Repository
public interface PlayerSocialRepository extends JpaRepository<PlayerSocial, String> {
    
    /**
     * 根据球队ID查询
     */
    List<PlayerSocial> findByTeamId(Long teamId);
    
    /**
     * 根据球员ID查询
     */
    List<PlayerSocial> findByPlayerId(Long playerId);
    
    /**
     * 根据平台查询
     */
    List<PlayerSocial> findByPlatform(String platform);
    
    /**
     * 根据球队ID和平台查询
     */
    List<PlayerSocial> findByTeamIdAndPlatform(Long teamId, String platform);
}
