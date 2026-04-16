package com.premierleague.server.service;

import com.premierleague.server.config.props.FetchSourceProperties;
import com.premierleague.server.entity.FetchLog;
import com.premierleague.server.entity.News;
import com.premierleague.server.provider.NewsProvider;
import com.premierleague.server.repository.FetchLogRepository;
import com.premierleague.server.repository.NewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 资讯抓取服务
 * 负责调度各 Provider 抓取数据、去重、清洗并入库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsFetchService {
    
    private final List<NewsProvider> providers;
    private final NewsRepository newsRepository;
    private final FetchLogRepository fetchLogRepository;
    private final DuplicateCheckService duplicateCheckService;
    private final ContentCleanService contentCleanService;
    private final FetchSourceProperties fetchProps;
    
    /**
     * 执行指定频率级别的抓取任务
     * @param frequencyLevel high / medium / low
     */
    @Transactional
    public void fetchByFrequency(String frequencyLevel) {
        List<String> sources = getSourcesByFrequency(frequencyLevel);
        
        log.info("[Fetch] Starting {} frequency fetch for sources: {}", frequencyLevel, sources);
        
        for (String sourceType : sources) {
            providers.stream()
                    .filter(p -> p.getSourceType().equals(sourceType))
                    .filter(NewsProvider::isAvailable)
                    .findFirst()
                    .ifPresent(provider -> fetchFromProvider(provider, frequencyLevel));
        }
    }
    
    /**
     * 从单个 Provider 抓取
     */
    @Transactional
    public void fetchFromProvider(NewsProvider provider, String frequencyLevel) {
        String sourceType = provider.getSourceType();
        String batchId = generateBatchId(sourceType);
        int maxItems = getMaxItemsByFrequency(frequencyLevel);
        
        FetchLog logEntry = FetchLog.builder()
                .batchId(batchId)
                .sourceType(sourceType)
                .frequencyLevel(frequencyLevel)
                .startedAt(LocalDateTime.now())
                .status("running")
                .build();
        
        fetchLogRepository.save(logEntry);
        
        int newCount = 0;
        int duplicateCount = 0;
        int failedCount = 0;
        int updatedCount = 0;
        
        try {
            // 1. 抓取原始数据
            List<News> fetchedNews = provider.fetchLatest(maxItems);
            logEntry.setRequestCount(fetchedNews.size());
            log.info("[Fetch] {} fetched {} items", sourceType, fetchedNews.size());
            
            for (News news : fetchedNews) {
                try {
                    // 2. 数据清洗
                    news = contentCleanService.cleanNews(news);
                    
                    // 3. 设置抓取时间
                    news.setFetchedAt(LocalDateTime.now());
                    news.setFetchBatchId(batchId);
                    
                    // 4. 检查并设置指纹
                    String fingerprint = duplicateCheckService.generateFingerprint(news);
                    boolean isDuplicate = newsRepository.existsByFingerprint(fingerprint);
                    
                    if (isDuplicate) {
                        // 检查是否需要更新
                        News existing = newsRepository.findByFingerprint(fingerprint).orElse(null);
                        if (existing != null && shouldUpdate(existing, news)) {
                            existing.setContent(news.getContent());
                            existing.setHotScore(news.getHotScore());
                            existing.setContentUpdatedAt(LocalDateTime.now());
                            existing.setFetchBatchId(batchId);
                            newsRepository.save(existing);
                            updatedCount++;
                            log.debug("[Fetch] Updated existing news: {}", fingerprint);
                        } else {
                            duplicateCount++;
                        }
                        continue;
                    }
                    
                    // 5. 设置指纹和ID
                    news.setFingerprint(fingerprint);
                    news.setId(fingerprint);
                    
                    // 6. 提取标签（如果没有）
                    if (news.getTags() == null || news.getTags().isEmpty()) {
                        String extractedTags = contentCleanService.extractTags(
                                news.getTitle(), news.getContent());
                        if (!extractedTags.isEmpty()) {
                            news.setTags(extractedTags);
                        }
                    }
                    
                    // 7. 保存到数据库
                    newsRepository.save(news);
                    newCount++;
                    
                } catch (Exception e) {
                    log.error("[Fetch] Failed to process news: {}", news.getTitle(), e);
                    failedCount++;
                }
            }
            
            // 8. 更新日志
            logEntry.setNewCount(newCount);
            logEntry.setUpdatedCount(updatedCount);
            logEntry.setDuplicateCount(duplicateCount);
            logEntry.setFailedCount(failedCount);
            logEntry.complete();
            
            log.info("[Fetch] Completed {}: new={}, updated={}, duplicate={}, failed={}", 
                    sourceType, newCount, updatedCount, duplicateCount, failedCount);
            
        } catch (Exception e) {
            log.error("[Fetch] Failed to fetch from {}", sourceType, e);
            logEntry.fail(e.getMessage());
        }
        
        fetchLogRepository.save(logEntry);
    }
    
    /**
     * 全量抓取（所有源）
     */
    @Transactional
    public void fetchAll() {
        log.info("[Fetch] Starting full fetch for all providers");
        
        for (NewsProvider provider : providers) {
            if (provider.isAvailable()) {
                fetchFromProvider(provider, provider.getFrequencyLevel());
            }
        }
    }
    
    /**
     * 获取抓取统计
     */
    public Map<String, Object> getFetchStats(String sourceType, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        List<Object[]> stats = fetchLogRepository.getStatsBySourceType(sourceType, since);
        
        if (stats.isEmpty() || stats.get(0)[0] == null) {
            return Map.of(
                    "fetchCount", 0,
                    "totalNew", 0,
                    "totalDuplicate", 0
            );
        }
        
        Object[] row = stats.get(0);
        return Map.of(
                "fetchCount", ((Number) row[0]).intValue(),
                "totalNew", row[1] != null ? ((Number) row[1]).intValue() : 0,
                "totalDuplicate", row[2] != null ? ((Number) row[2]).intValue() : 0
        );
    }
    
    /**
     * 判断是否更新已有新闻
     */
    private boolean shouldUpdate(News existing, News fetched) {
        // 内容长度有显著变化
        if (existing.getContent() != null && fetched.getContent() != null) {
            int existingLen = existing.getContent().length();
            int fetchedLen = fetched.getContent().length();
            // 新内容比旧内容长20%以上
            if (fetchedLen > existingLen * 1.2) {
                return true;
            }
        }
        
        // 热度值变化较大
        if (existing.getHotScore() != null && fetched.getHotScore() != null) {
            if (Math.abs(fetched.getHotScore() - existing.getHotScore()) > 10) {
                return true;
            }
        }
        
        return false;
    }
    
    // ========== 私有方法 ==========
    
    private List<String> getSourcesByFrequency(String frequencyLevel) {
        return switch (frequencyLevel.toLowerCase()) {
            case "high" -> fetchProps.getHighFrequency().getSources();
            case "medium" -> fetchProps.getMediumFrequency().getSources();
            case "low" -> fetchProps.getLowFrequency().getSources();
            default -> List.of();
        };
    }
    
    private int getMaxItemsByFrequency(String frequencyLevel) {
        return switch (frequencyLevel.toLowerCase()) {
            case "high" -> fetchProps.getHighFrequency().getMaxItemsPerFetch();
            case "medium" -> fetchProps.getMediumFrequency().getMaxItemsPerFetch();
            case "low" -> fetchProps.getLowFrequency().getMaxItemsPerFetch();
            default -> 50;
        };
    }
    
    private String generateBatchId(String sourceType) {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "_" + sourceType;
    }
}
