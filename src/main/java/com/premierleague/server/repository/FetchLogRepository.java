package com.premierleague.server.repository;

import com.premierleague.server.entity.FetchLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 抓取日志 Repository
 */
@Repository
public interface FetchLogRepository extends JpaRepository<FetchLog, Long> {
    
    /**
     * 根据批次号查询
     */
    Optional<FetchLog> findByBatchId(String batchId);
    
    /**
     * 查询最近 N 次抓取日志
     */
    List<FetchLog> findTop10ByOrderByCreatedAtDesc();
    
    /**
     * 查询某来源最近一次的抓取日志
     */
    Optional<FetchLog> findTopBySourceTypeOrderByCreatedAtDesc(String sourceType);
    
    /**
     * 查询某来源在时间范围内的抓取统计
     */
    @Query("SELECT COUNT(f), SUM(f.newCount), SUM(f.duplicateCount) FROM FetchLog f " +
           "WHERE f.sourceType = :sourceType AND f.createdAt >= :since")
    List<Object[]> getStatsBySourceType(@Param("sourceType") String sourceType, 
                                        @Param("since") LocalDateTime since);
    
    /**
     * 查询某时间范围内失败的抓取任务
     */
    List<FetchLog> findByStatusAndCreatedAtAfter(String status, LocalDateTime since);
}
