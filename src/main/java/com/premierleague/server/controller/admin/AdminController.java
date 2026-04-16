package com.premierleague.server.controller.admin;

import com.premierleague.server.dto.ApiResponse;
import com.premierleague.server.entity.FetchLog;
import com.premierleague.server.provider.NewsProvider;
import com.premierleague.server.repository.FetchLogRepository;
import com.premierleague.server.service.NewsFetchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理后台接口
 * 用于手动触发抓取、查看日志等管理操作
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    
    private final NewsFetchService newsFetchService;
    private final FetchLogRepository fetchLogRepository;
    private final List<NewsProvider> providers;
    
    /**
     * 手动触发指定频率的抓取任务
     * POST /api/admin/fetch/{frequency}
     * frequency: high / medium / low
     */
    @PostMapping("/fetch/{frequency}")
    public ApiResponse<Map<String, Object>> triggerFetch(@PathVariable String frequency) {
        log.info("[Admin] Manual trigger fetch: frequency={}", frequency);
        
        long startTime = System.currentTimeMillis();
        
        try {
            newsFetchService.fetchByFrequency(frequency);
            
            Map<String, Object> result = new HashMap<>();
            result.put("frequency", frequency);
            result.put("triggeredAt", LocalDateTime.now().toString());
            result.put("durationMs", System.currentTimeMillis() - startTime);
            result.put("status", "success");
            
            return ApiResponse.ok(result);
        } catch (Exception e) {
            log.error("[Admin] Manual fetch failed", e);
            return ApiResponse.error("Fetch failed: " + e.getMessage());
        }
    }
    
    /**
     * 手动触发指定源的抓取
     * POST /api/admin/fetch/source/{sourceType}
     */
    @PostMapping("/fetch/source/{sourceType}")
    public ApiResponse<Map<String, Object>> triggerFetchBySource(@PathVariable String sourceType) {
        log.info("[Admin] Manual trigger fetch by source: {}", sourceType);
        
        NewsProvider provider = providers.stream()
                .filter(p -> p.getSourceType().equals(sourceType))
                .findFirst()
                .orElse(null);
        
        if (provider == null) {
            return ApiResponse.error("Unknown source type: " + sourceType);
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            newsFetchService.fetchFromProvider(provider, provider.getFrequencyLevel());
            
            Map<String, Object> result = new HashMap<>();
            result.put("sourceType", sourceType);
            result.put("sourceName", provider.getSourceName());
            result.put("triggeredAt", LocalDateTime.now().toString());
            result.put("durationMs", System.currentTimeMillis() - startTime);
            result.put("status", "success");
            
            return ApiResponse.ok(result);
        } catch (Exception e) {
            log.error("[Admin] Manual fetch failed", e);
            return ApiResponse.error("Fetch failed: " + e.getMessage());
        }
    }
    
    /**
     * 获取最近抓取日志
     * GET /api/admin/logs?limit=20
     */
    @GetMapping("/logs")
    public ApiResponse<List<FetchLog>> getRecentLogs(
            @RequestParam(defaultValue = "20") int limit) {
        List<FetchLog> logs = fetchLogRepository.findTop10ByOrderByCreatedAtDesc();
        return ApiResponse.ok(logs);
    }
    
    /**
     * 获取抓取统计
     * GET /api/admin/stats?hours=24
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats(
            @RequestParam(defaultValue = "24") int hours) {
        
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        // 获取所有源的统计
        Map<String, Object> stats = new HashMap<>();
        stats.put("periodHours", hours);
        stats.put("since", since.toString());
        
        Map<String, Object> sourceStats = new HashMap<>();
        for (NewsProvider provider : providers) {
            Map<String, Object> providerStats = newsFetchService.getFetchStats(
                    provider.getSourceType(), hours);
            sourceStats.put(provider.getSourceType(), providerStats);
        }
        stats.put("bySource", sourceStats);
        
        // 获取失败的任务
        List<FetchLog> failedLogs = fetchLogRepository.findByStatusAndCreatedAtAfter("failed", since);
        stats.put("failedCount", failedLogs.size());
        stats.put("recentFailures", failedLogs.stream()
                .map(log -> Map.of(
                        "batchId", log.getBatchId(),
                        "sourceType", log.getSourceType(),
                        "error", log.getErrorMessage() != null ? log.getErrorMessage() : "Unknown"
                ))
                .collect(Collectors.toList()));
        
        return ApiResponse.ok(stats);
    }
    
    /**
     * 获取所有源的状态
     * GET /api/admin/sources
     */
    @GetMapping("/sources")
    public ApiResponse<List<Map<String, Object>>> getSourceStatus() {
        List<Map<String, Object>> sources = providers.stream()
                .map(provider -> {
                    Map<String, Object> info = new HashMap<>();
                    info.put("sourceType", provider.getSourceType());
                    info.put("sourceName", provider.getSourceName());
                    info.put("frequencyLevel", provider.getFrequencyLevel());
                    info.put("available", provider.isAvailable());
                    
                    // 获取最近一次抓取
                    fetchLogRepository.findTopBySourceTypeOrderByCreatedAtDesc(provider.getSourceType())
                            .ifPresent(log -> {
                                info.put("lastFetchAt", log.getCreatedAt().toString());
                                info.put("lastFetchStatus", log.getStatus());
                                info.put("lastFetchNewCount", log.getNewCount());
                            });
                    
                    return info;
                })
                .collect(Collectors.toList());
        
        return ApiResponse.ok(sources);
    }
    
    /**
     * 健康检查
     * GET /api/admin/health
     */
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now().toString());
        health.put("sources", providers.size());
        health.put("sourcesAvailable", providers.stream().filter(NewsProvider::isAvailable).count());
        
        return ApiResponse.ok(health);
    }
}
