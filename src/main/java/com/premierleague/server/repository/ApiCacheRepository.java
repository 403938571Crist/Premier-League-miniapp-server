package com.premierleague.server.repository;

import com.premierleague.server.entity.ApiCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ApiCacheRepository extends JpaRepository<ApiCache, String> {

    /** 查未过期的缓存 */
    @Query("SELECT c FROM ApiCache c WHERE c.cacheKey = :key AND c.expiresAt > :now")
    Optional<ApiCache> findValidByKey(@Param("key") String key, @Param("now") LocalDateTime now);

    /** 删已过期行（定时清理用） */
    @Modifying
    @Transactional
    @Query("DELETE FROM ApiCache c WHERE c.expiresAt <= :now")
    int deleteExpired(@Param("now") LocalDateTime now);

    /** 按前缀删（evict 整个 domain，如清掉所有 topScorers:* ） */
    @Modifying
    @Transactional
    @Query("DELETE FROM ApiCache c WHERE c.cacheKey LIKE :prefix%")
    int deleteByKeyPrefix(@Param("prefix") String prefix);

    /** 查所有有效缓存的概要（监控用） */
    @Query("SELECT c.cacheKey, c.createdAt, c.expiresAt, c.hitCount FROM ApiCache c WHERE c.expiresAt > :now")
    List<Object[]> findSummaryValid(@Param("now") LocalDateTime now);
}
