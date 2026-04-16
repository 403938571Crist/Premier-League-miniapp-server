package com.premierleague.server.repository;

import com.premierleague.server.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 比赛 Repository
 */
@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {
    
    /**
     * 根据API ID查询
     */
    Optional<Match> findByApiId(Long apiId);
    
    /**
     * 查询某轮的所有比赛
     */
    List<Match> findByMatchdayOrderByMatchDateAsc(Integer matchday);
    
    /**
     * 查询当前轮次的比赛
     */
    @Query("SELECT m FROM Match m WHERE m.matchday = (SELECT MAX(m2.matchday) FROM Match m2 WHERE m2.status = 'FINISHED') + 1")
    List<Match> findCurrentMatchday();
    
    /**
     * 查询球队的所有比赛
     */
    @Query("SELECT m FROM Match m WHERE m.homeTeamId = :teamId OR m.awayTeamId = :teamId ORDER BY m.matchDate DESC")
    List<Match> findByTeamId(@Param("teamId") Long teamId);
    
    /**
     * 查询某日期范围内的比赛
     */
    List<Match> findByMatchDateBetweenOrderByMatchDateAsc(LocalDateTime start, LocalDateTime end);
    
    /**
     * 查询今日比赛
     */
    @Query("SELECT m FROM Match m WHERE DATE(m.matchDate) = CURRENT_DATE ORDER BY m.matchDate")
    List<Match> findTodayMatches();
    
    /**
     * 查询进行中的比赛
     */
    List<Match> findByStatusInOrderByMatchDateDesc(List<String> statuses);
    
    /**
     * 查询已完成的比赛
     */
    List<Match> findByStatusOrderByMatchDateDesc(String status);
    
    /**
     * 查询最近5场比赛（用于球队详情）
     */
    @Query("SELECT m FROM Match m WHERE (m.homeTeamId = :teamId OR m.awayTeamId = :teamId) AND m.status = 'FINISHED' ORDER BY m.matchDate DESC")
    List<Match> findLast5MatchesByTeamId(@Param("teamId") Long teamId);
    
    /**
     * 查询两队的交锋记录
     */
    @Query("SELECT m FROM Match m WHERE ((m.homeTeamId = :team1 AND m.awayTeamId = :team2) OR (m.homeTeamId = :team2 AND m.awayTeamId = :team1)) AND m.status = 'FINISHED' ORDER BY m.matchDate DESC")
    List<Match> findHeadToHead(@Param("team1") Long team1, @Param("team2") Long team2);
    
    /**
     * 获取最大轮次
     */
    @Query("SELECT MAX(m.matchday) FROM Match m")
    Integer findMaxMatchday();
}
