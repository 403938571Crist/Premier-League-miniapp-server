package com.premierleague.server.repository;

import com.premierleague.server.entity.News;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NewsRepository extends JpaRepository<News, String>, JpaSpecificationExecutor<News> {

    Optional<News> findByFingerprint(String fingerprint);

    boolean existsByFingerprint(String fingerprint);

    Page<News> findBySourceTypeOrderBySourcePublishedAtDesc(String sourceType, Pageable pageable);

    Page<News> findByMediaTypeOrderBySourcePublishedAtDesc(String mediaType, Pageable pageable);

    @Query("SELECT n FROM News n WHERE n.title LIKE %:keyword% OR n.summary LIKE %:keyword% ORDER BY n.sourcePublishedAt DESC")
    Page<News> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT n FROM News n WHERE n.mediaType = 'transfer' OR n.tags LIKE %:tag% ORDER BY n.sourcePublishedAt DESC")
    List<News> findTransferNews(@Param("tag") String tag);

    @Query("SELECT n FROM News n WHERE n.tags LIKE %:tag% ORDER BY n.sourcePublishedAt DESC")
    Page<News> findByTag(@Param("tag") String tag, Pageable pageable);

    Page<News> findAllByOrderBySourcePublishedAtDesc(Pageable pageable);

    List<News> findTop10ByOrderBySourcePublishedAtDesc();
}
