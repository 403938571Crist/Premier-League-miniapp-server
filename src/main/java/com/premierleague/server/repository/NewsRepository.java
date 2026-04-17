package com.premierleague.server.repository;

import com.premierleague.server.entity.News;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 资讯 Repository
 */
@Repository
public interface NewsRepository extends JpaRepository<News, String> {
    
    /**
     * 根据指纹查询
     */
    Optional<News> findByFingerprint(String fingerprint);
    
    /**
     * 检查指纹是否存在
     */
    boolean existsByFingerprint(String fingerprint);
    
    /**
     * 根据来源类型分页查询
     */
    Page<News> findBySourceTypeOrderBySourcePublishedAtDesc(String sourceType, Pageable pageable);
    
    /**
     * 根据媒体类型分页查询
     */
    Page<News> findByMediaTypeOrderBySourcePublishedAtDesc(String mediaType, Pageable pageable);
    
    /**
     * 根据标题或摘要模糊搜索
     */
    @Query("SELECT n FROM News n WHERE n.title LIKE %:keyword% OR n.summary LIKE %:keyword% ORDER BY n.sourcePublishedAt DESC")
    Page<News> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
    
    /**
     * 查询转会资讯
     */
    @Query("SELECT n FROM News n WHERE n.mediaType = 'transfer' OR n.tags LIKE %:tag% ORDER BY n.sourcePublishedAt DESC")
    List<News> findTransferNews(@Param("tag") String tag);
    
    /**
     * 根据标签查询
     */
    @Query("SELECT n FROM News n WHERE n.tags LIKE %:tag% ORDER BY n.sourcePublishedAt DESC")
    Page<News> findByTag(@Param("tag") String tag, Pageable pageable);
    
    /**
     * 按发布时间降序排序（最新优先）
     */
    Page<News> findAllByOrderBySourcePublishedAtDesc(Pageable pageable);
    
    /**
     * 查询最新的资讯
     */
    List<News> findTop10ByOrderBySourcePublishedAtDesc();
}
