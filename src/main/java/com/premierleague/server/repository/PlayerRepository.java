package com.premierleague.server.repository;

import com.premierleague.server.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 球员 Repository
 */
@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {
    
    /**
     * 根据API ID查询
     */
    Optional<Player> findByApiId(Long apiId);
    
    /**
     * 查询球队的所有球员
     */
    List<Player> findByTeamIdOrderByPositionAscShirtNumberAsc(Long teamId);
    
    /**
     * 按位置分组查询球员
     */
    List<Player> findByTeamIdAndPositionOrderByShirtNumberAsc(Long teamId, String position);
    
    /**
     * 查询门将
     */
    @Query("SELECT p FROM Player p WHERE p.teamId = :teamId AND p.position = 'Goalkeeper' ORDER BY p.shirtNumber")
    List<Player> findGoalkeepersByTeamId(@Param("teamId") Long teamId);
    
    /**
     * 查询后卫
     */
    @Query("SELECT p FROM Player p WHERE p.teamId = :teamId AND p.position = 'Defender' ORDER BY p.shirtNumber")
    List<Player> findDefendersByTeamId(@Param("teamId") Long teamId);
    
    /**
     * 查询中场
     */
    @Query("SELECT p FROM Player p WHERE p.teamId = :teamId AND p.position = 'Midfielder' ORDER BY p.shirtNumber")
    List<Player> findMidfieldersByTeamId(@Param("teamId") Long teamId);
    
    /**
     * 查询前锋
     */
    @Query("SELECT p FROM Player p WHERE p.teamId = :teamId AND p.position = 'Attacker' ORDER BY p.shirtNumber")
    List<Player> findAttackersByTeamId(@Param("teamId") Long teamId);
    
    /**
     * 搜索球员
     */
    @Query("SELECT p FROM Player p WHERE p.name LIKE %:keyword% OR p.chineseName LIKE %:keyword%")
    List<Player> searchByName(@Param("keyword") String keyword);
    
    /**
     * 查询身价最高的球员
     */
    @Query("SELECT p FROM Player p WHERE p.teamId = :teamId AND p.marketValue IS NOT NULL ORDER BY p.marketValue DESC")
    List<Player> findMostValuablePlayersByTeamId(@Param("teamId") Long teamId);
}
