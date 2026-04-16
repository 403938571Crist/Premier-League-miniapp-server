package com.premierleague.server.repository;

import com.premierleague.server.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 球队 Repository
 */
@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {
    
    /**
     * 根据API ID查询
     */
    Optional<Team> findByApiId(Long apiId);
    
    /**
     * 根据简称查询
     */
    Optional<Team> findByShortName(String shortName);
    
    /**
     * 根据中文名查询
     */
    Optional<Team> findByChineseName(String chineseName);
    
    /**
     * 按积分排名查询（积分榜）
     */
    @Query("SELECT t FROM Team t WHERE t.position IS NOT NULL ORDER BY t.position ASC")
    List<Team> findStandings();
    
    /**
     * 查询前N名球队
     */
    List<Team> findTop4ByPositionIsNotNullOrderByPositionAsc();
    
    /**
     * 查询欧冠区球队（前4）
     */
    @Query("SELECT t FROM Team t WHERE t.position <= 4 ORDER BY t.position")
    List<Team> findChampionsLeagueTeams();
    
    /**
     * 查询欧联区球队（5-6）
     */
    @Query("SELECT t FROM Team t WHERE t.position BETWEEN 5 AND 6 ORDER BY t.position")
    List<Team> findEuropaLeagueTeams();
    
    /**
     * 查询降级区球队（最后3名）
     */
    @Query("SELECT t FROM Team t WHERE t.position >= 18 ORDER BY t.position")
    List<Team> findRelegationTeams();
}
