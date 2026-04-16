package com.premierleague.server.repository;

import com.premierleague.server.entity.SyncLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 同步日志 Repository
 */
@Repository
public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {

    /**
     * 根据同步类型查询最新日志
     */
    Optional<SyncLog> findTopBySyncTypeOrderBySyncTimeDesc(String syncType);

    /**
     * 查询某类型的最近N条日志
     */
    List<SyncLog> findTop10BySyncTypeOrderBySyncTimeDesc(String syncType);

    /**
     * 查询某时间范围内的日志
     */
    List<SyncLog> findBySyncTimeBetweenOrderBySyncTimeDesc(LocalDateTime start, LocalDateTime end);

    /**
     * 查询最近的同步日志
     */
    @Query("SELECT s FROM SyncLog s ORDER BY s.syncTime DESC")
    List<SyncLog> findRecentLogs(org.springframework.data.domain.Pageable pageable);

    /**
     * 查询某状态的所有日志
     */
    List<SyncLog> findByStatusOrderBySyncTimeDesc(String status);

    /**
     * 统计某类型的同步次数
     */
    long countBySyncType(String syncType);

    /**
     * 统计某类型的成功次数
     */
    long countBySyncTypeAndStatus(String syncType, String status);
}
