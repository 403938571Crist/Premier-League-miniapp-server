package com.premierleague.server.service;

import com.premierleague.server.dto.PageResult;
import com.premierleague.server.entity.News;
import com.premierleague.server.model.ArticleBlock;
import com.premierleague.server.model.NewsArticle;
import com.premierleague.server.model.NewsListItem;
import com.premierleague.server.model.TransferNews;
import com.premierleague.server.repository.NewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 资讯服务
 * 
 * 【重要】首页资讯流统一走服务端缓存结果，不实时硬抓
 * - 数据由定时任务抓取到 MySQL
 * - 查询时从 MySQL 读取，走 Caffeine 本地缓存
 * - 数据库为空时返回空结果（无 Mock 数据）
 */
@Slf4j
@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = "news")
public class NewsService {
    
    private final NewsRepository newsRepository;
    
    /**
     * 获取资讯列表 - GET /api/news
     * 
     * 【缓存策略】缓存 5 分钟
     * 首页资讯流不走实时抓取，只读数据库 + 缓存
     */
    @Cacheable(value = "newsList", key = "#page+'-'+#pageSize+'-'+#sourceType+'-'+#tag+'-'+#keyword")
    public PageResult<NewsListItem> getNewsList(
            int page,
            int pageSize,
            String sourceType,
            String tag,
            String keyword
    ) {
        log.debug("[NewsService] Getting news list from DB: page={}, size={}", page, pageSize);
        
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        Page<News> newsPage;
        
        // 从数据库查询（不走实时抓取）
        if (keyword != null && !keyword.isEmpty()) {
            newsPage = newsRepository.searchByKeyword(keyword, pageable);
        } else if (sourceType != null && !sourceType.isEmpty()) {
            newsPage = newsRepository.findBySourceTypeOrderBySourcePublishedAtDesc(sourceType, pageable);
        } else if (tag != null && !tag.isEmpty()) {
            newsPage = newsRepository.findByTag(tag, pageable);
        } else {
            newsPage = newsRepository.findAllByOrderByHotScoreDescThenTimeDesc(pageable);
        }
        
        List<NewsListItem> items = newsPage.getContent().stream()
                .map(this::convertToListItem)
                .collect(Collectors.toList());
        
        log.debug("[NewsService] Returning {} items from DB", items.size());
        return PageResult.of(items, page, pageSize, newsPage.getTotalElements());
    }
    
    /**
     * 获取资讯详情 - GET /api/news/{id}
     * 
     * 【缓存策略】缓存 30 分钟
     */
    @Cacheable(value = "newsDetail", key = "#id")
    public Optional<NewsArticle> getNewsDetail(String id) {
        log.debug("[NewsService] Getting news detail from DB: id={}", id);
        
        return newsRepository.findById(id)
                .map(this::convertToArticle);
    }
    
    /**
     * 获取转会快讯 - GET /api/news/transfers
     * 
     * 【缓存策略】缓存 2 分钟（更新频繁）
     */
    @Cacheable(value = "transferNews", key = "#source+'-'+#teamId+'-'+#playerId")
    public List<TransferNews> getTransferNews(String source, Long teamId, Long playerId) {
        log.debug("[NewsService] Getting transfer news from DB");
        
        // 从数据库查询转会资讯
        List<News> transfers = newsRepository.findTransferNews("转会");
        
        return transfers.stream()
                .filter(item -> source == null || item.getSourceType().equals(source))
                .filter(item -> teamId == null || 
                        (item.getRelatedTeamIds() != null && item.getRelatedTeamIds().contains(teamId.toString())))
                .map(this::convertToTransferNews)
                .collect(Collectors.toList());
    }
    
    // ========== 转换方法 ==========
    
    private NewsListItem convertToListItem(News news) {
        return new NewsListItem(
                news.getId(),
                news.getTitle(),
                news.getSummary(),
                news.getSource(),
                news.getSourceType(),
                news.getMediaType(),
                news.getSourcePublishedAt().toString(),
                news.getCoverImage(),
                news.getTags() != null ? Arrays.asList(news.getTags().split(",")) : List.of(),
                news.getHotScore(),
                news.getAuthor(),
                news.getUrl()
        );
    }
    
    private NewsArticle convertToArticle(News news) {
        return new NewsArticle(
                news.getId(),
                news.getTitle(),
                news.getSummary(),
                news.getSource(),
                news.getSourceType(),
                news.getMediaType(),
                news.getSourcePublishedAt().toString(),
                news.getAuthor(),
                news.getCoverImage(),
                news.getTags() != null ? Arrays.asList(news.getTags().split(",")) : List.of(),
                news.getRelatedTeamIds() != null ? 
                        Arrays.stream(news.getRelatedTeamIds().split(","))
                                .map(Long::parseLong)
                                .collect(Collectors.toList()) : List.of(),
                news.getRelatedPlayerIds() != null ? 
                        Arrays.stream(news.getRelatedPlayerIds().split(","))
                                .map(Long::parseLong)
                                .collect(Collectors.toList()) : List.of(),
                news.getHotScore(),
                news.getUrl(),
                news.getSourceNote(),
                parseBlocks(news.getContent())
        );
    }
    
    private TransferNews convertToTransferNews(News news) {
        return new TransferNews(
                news.getId(),
                news.getTitle(),
                news.getSummary(),
                news.getSource(),
                news.getSourceType(),
                news.getSourcePublishedAt().toString(),
                news.getRelatedTeamIds() != null ? 
                        Arrays.stream(news.getRelatedTeamIds().split(","))
                                .map(Long::parseLong)
                                .collect(Collectors.toList()) : List.of(),
                news.getRelatedPlayerIds() != null ? 
                        Arrays.stream(news.getRelatedPlayerIds().split(","))
                                .map(Long::parseLong)
                                .collect(Collectors.toList()) : List.of(),
                news.getHotScore(),
                news.getUrl()
        );
    }
    
    private List<ArticleBlock> parseBlocks(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(content.split("\n\n"))
                .filter(p -> !p.trim().isEmpty())
                .map(ArticleBlock::paragraph)
                .collect(Collectors.toList());
    }
}
