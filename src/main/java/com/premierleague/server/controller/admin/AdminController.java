package com.premierleague.server.controller.admin;

import com.premierleague.server.dto.ApiResponse;
import com.premierleague.server.entity.FetchLog;
import com.premierleague.server.entity.News;
import com.premierleague.server.provider.DongqiudiProvider;
import com.premierleague.server.provider.NewsProvider;
import com.premierleague.server.repository.FetchLogRepository;
import com.premierleague.server.repository.NewsRepository;
import com.premierleague.server.service.NewsFetchService;
import com.premierleague.server.service.PlayerProfileBackfillService;
import com.premierleague.server.service.PlayerSocialBackfillService;
import com.premierleague.server.service.PlayerSquadBackfillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
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
    private final PlayerProfileBackfillService playerProfileBackfillService;
    private final PlayerSquadBackfillService playerSquadBackfillService;
    private final PlayerSocialBackfillService playerSocialBackfillService;
    private final FetchLogRepository fetchLogRepository;
    private final NewsRepository newsRepository;
    private final DongqiudiProvider dongqiudiProvider;
    private final List<NewsProvider> providers;
    private final CacheManager cacheManager;
    
    /**
     * 手动触发指定频率的抓取任务
     * POST /api/admin/fetch/{frequency}
     * frequency: high / medium / low
     */
    @PostMapping("/fetch/{frequency}")
    public ApiResponse<Map<String, Object>> triggerFetch(@PathVariable String frequency) {
        log.info("[Admin] Manual trigger fetch: frequency={}", frequency);

        long startTime = System.currentTimeMillis();
        // 失败让 GlobalExceptionHandler 接住 —— 避免原始 e.getMessage() 经 ApiResponse 漏给客户端
        // （抓取层的 IOException 里常带内网 URL 和 provider 返回的 headers/body 片段）
        newsFetchService.fetchByFrequency(frequency);

        Map<String, Object> result = new HashMap<>();
        result.put("frequency", frequency);
        result.put("triggeredAt", LocalDateTime.now().toString());
        result.put("durationMs", System.currentTimeMillis() - startTime);
        result.put("status", "success");

        return ApiResponse.ok(result);
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

        // 这两个是"参数校验"错误，message 是我们自己写的常量，透传安全
        if (provider == null) {
            throw new IllegalArgumentException("Unknown source type: " + sourceType);
        }
        if (!provider.isEnabled()) {
            throw new IllegalArgumentException("Source disabled: " + sourceType);
        }

        long startTime = System.currentTimeMillis();
        // 抓取失败让 GlobalExceptionHandler 接住（同 triggerFetch 注释）
        newsFetchService.fetchFromProvider(provider, provider.getFrequencyLevel());

        // 抓取成功后立刻清 newsList 缓存，保证 /api/news 能看到新数据
        var cache = cacheManager.getCache("newsList");
        if (cache != null) cache.clear();

        Map<String, Object> result = new HashMap<>();
        result.put("sourceType", sourceType);
        result.put("sourceName", provider.getSourceName());
        result.put("triggeredAt", LocalDateTime.now().toString());
        result.put("durationMs", System.currentTimeMillis() - startTime);
        result.put("status", "success");

        return ApiResponse.ok(result);
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
        for (NewsProvider provider : providers.stream().filter(NewsProvider::isEnabled).toList()) {
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
                .filter(NewsProvider::isEnabled)
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
     * 懂球帝正文批量回填
     * POST /api/admin/backfill/dongqiudi?limit=50
     * 对 content 为空的懂球帝文章，从 PC 页抓取正文并更新
     */
    @PostMapping("/backfill/dongqiudi")
    public ApiResponse<Map<String, Object>> backfillDongqiudi(
            @RequestParam(defaultValue = "50") int limit) {
        log.info("[Admin] Starting dongqiudi content backfill, limit={}", limit);

        List<News> articles = newsRepository
                .findBySourceTypeOrderBySourcePublishedAtDesc("dongqiudi", PageRequest.of(0, limit))
                .getContent()
                .stream()
                .filter(n -> n.getContent() == null || n.getContent().isEmpty())
                .toList();

        int updated = 0, failed = 0;
        for (News article : articles) {
            String articleId = extractDongqiudiId(article.getUrl());
            if (articleId == null) { failed++; continue; }
            try {
                String content = dongqiudiProvider.fetchArticleContent(articleId);
                if (content != null && !content.isEmpty()) {
                    article.setContent(content);
                    newsRepository.save(article);
                    updated++;
                }
                Thread.sleep(300);
            } catch (Exception e) {
                log.warn("[Admin] Backfill failed for {}: {}", articleId, e.getMessage());
                failed++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", articles.size());
        result.put("updated", updated);
        result.put("failed", failed);
        log.info("[Admin] Dongqiudi backfill done: {}", result);
        return ApiResponse.ok(result);
    }

    @PostMapping("/backfill/news")
    public ApiResponse<Map<String, Object>> backfillNews(
            @RequestParam(required = false) String sourceType,
            @RequestParam(defaultValue = "50") int limit) {
        log.info("[Admin] Starting news backfill, sourceType={}, limit={}", sourceType, limit);

        Map<String, Object> result = newsFetchService.backfillNews(sourceType, limit);
        clearNewsCaches();
        return ApiResponse.ok(result);
    }

    @GetMapping("/backfill/news/{id}/inspect")
    public ApiResponse<Map<String, Object>> inspectBackfillNews(@PathVariable String id) {
        return ApiResponse.ok(newsFetchService.inspectBackfillState(id));
    }

    @PostMapping("/backfill/news/{id}")
    public ApiResponse<Map<String, Object>> backfillNewsById(@PathVariable String id) {
        log.info("[Admin] Starting news backfill by id={}", id);
        Map<String, Object> result = newsFetchService.backfillNewsById(id);
        clearNewsCaches();
        return ApiResponse.ok(result);
    }

    /** 从懂球帝 URL 中提取文章 ID */
    @PostMapping("/backfill/players")
    public ApiResponse<Map<String, Object>> backfillPlayers(
            @RequestParam(defaultValue = "150") int limit,
            @RequestParam(required = false) List<Long> teamIds) {
        log.info("[Admin] Starting player profile backfill, limit={}, teamIds={}", limit, teamIds);
        PlayerProfileBackfillService.BackfillResult result = (teamIds == null || teamIds.isEmpty())
                ? playerProfileBackfillService.backfillMissingProfiles(limit)
                : playerProfileBackfillService.backfillMissingProfiles(limit, teamIds);

        var squadCache = cacheManager.getCache("teamSquad");
        if (squadCache != null) {
            squadCache.clear();
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("teamIds", teamIds);
        payload.put("scanned", result.scanned());
        payload.put("updated", result.updatedPlayers());
        payload.put("chineseNamesUpdated", result.chineseNamesUpdated());
        payload.put("photosUpdated", result.photosUpdated());
        return ApiResponse.ok(payload);
    }

    @PostMapping("/backfill/players/big6")
    public ApiResponse<Map<String, Object>> backfillBig6Players(
            @RequestParam(defaultValue = "150") int limit) {
        List<Long> big6TeamIds = List.of(1L, 2L, 3L, 5L, 6L, 18L);
        log.info("[Admin] Starting big6 player profile backfill, limit={}", limit);
        PlayerProfileBackfillService.BackfillResult result =
                playerProfileBackfillService.backfillMissingProfiles(limit, big6TeamIds);

        var squadCache = cacheManager.getCache("teamSquad");
        if (squadCache != null) {
            squadCache.clear();
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("scope", "big6");
        payload.put("teamIds", big6TeamIds);
        payload.put("scanned", result.scanned());
        payload.put("updated", result.updatedPlayers());
        payload.put("chineseNamesUpdated", result.chineseNamesUpdated());
        payload.put("photosUpdated", result.photosUpdated());
        return ApiResponse.ok(payload);
    }

    @PostMapping("/backfill/players/squad")
    public ApiResponse<Map<String, Object>> backfillPlayerSquads(
            @RequestParam List<Long> teamIds) {
        log.info("[Admin] Starting official squad backfill, teamIds={}", teamIds);
        PlayerSquadBackfillService.BackfillResult result =
                playerSquadBackfillService.backfillOfficialSquads(teamIds);

        Map<String, Object> payload = new HashMap<>();
        payload.put("teamIds", teamIds);
        payload.put("scannedTeams", result.scannedTeams());
        payload.put("scannedPlayers", result.scannedPlayers());
        payload.put("createdPlayers", result.createdPlayers());
        payload.put("updatedPlayers", result.updatedPlayers());
        payload.put("photosUpdated", result.photosUpdated());
        return ApiResponse.ok(payload);
    }

    /**
     * 扫 FORCE_WIKIPEDIA_REFRESH 名单里的球员，强制刷新 photo_url。
     * 专治：被租借到非英超俱乐部（例：Rashford→Barcelona、Hojlund→Napoli）的球员，
     * Pulselive Man Utd squad 端已经不再返回他们，squad sync 摸不到，photo_url 永久卡在旧 CDN URL。
     * POST /api/admin/refresh-transfer-photos
     */
    @PostMapping("/refresh-transfer-photos")
    public ApiResponse<Map<String, Object>> refreshTransferPhotos() {
        log.info("[Admin] Refresh transfer photos");
        PlayerSquadBackfillService.TransferPhotoRefreshResult result =
                playerSquadBackfillService.refreshTransferPhotos();

        Map<String, Object> payload = new HashMap<>();
        payload.put("watchlistSize", result.watchlistSize());
        payload.put("scannedRows", result.scannedRows());
        payload.put("updatedRows", result.updatedRows());
        payload.put("updatedIdentifiers", result.updatedIdentifiers());
        return ApiResponse.ok(payload);
    }

    @PostMapping("/backfill/player-social")
    public ApiResponse<Map<String, Object>> backfillPlayerSocial(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) List<Long> teamIds) {
        log.info("[Admin] Starting player social backfill, limit={}, teamIds={}", limit, teamIds);
        PlayerSocialBackfillService.BackfillResult result =
                playerSocialBackfillService.backfillPlayerSocials(limit, teamIds);

        Map<String, Object> payload = new HashMap<>();
        payload.put("limit", limit);
        payload.put("teamIds", teamIds);
        payload.put("scannedPlayers", result.scannedPlayers());
        payload.put("playersWithProfiles", result.playersWithProfiles());
        payload.put("insertedProfiles", result.insertedProfiles());
        payload.put("updatedProfiles", result.updatedProfiles());
        return ApiResponse.ok(payload);
    }

    private String extractDongqiudiId(String url) {
        if (url == null) return null;
        // 兼容：/articles/12345.html、/articles/12345、/article/12345
        java.util.regex.Matcher m1 = java.util.regex.Pattern
                .compile("/articles?/(\\d+)(?:\\.html)?").matcher(url);
        if (m1.find()) return m1.group(1);
        // https://n.dongqiudi.com/webapp/news.html?articleId=5783671
        java.util.regex.Matcher m2 = java.util.regex.Pattern
                .compile("[?&]articleId=(\\d+)").matcher(url);
        if (m2.find()) return m2.group(1);
        return null;
    }

    /**
     * 清空所有资讯相关缓存（newsList / newsDetail / transferNews）
     * POST /api/admin/cache/evict
     * 抓取完新数据后调用，让 /api/news 立刻反映最新内容
     */
    @PostMapping("/cache/evict")
    public ApiResponse<Map<String, Object>> evictNewsCache() {
        String[] caches = clearNewsCaches();
        Map<String, Object> r = new HashMap<>();
        r.put("evicted", caches);
        r.put("at", LocalDateTime.now().toString());
        log.info("[Admin] News cache evicted: {}", java.util.Arrays.toString(caches));
        return ApiResponse.ok(r);
    }

    // /api/admin/health 已移除 —— 和 /actuator/health 功能重叠
    // 需要看 news provider 状态请用 GET /api/admin/sources

    private String[] clearNewsCaches() {
        String[] caches = {"newsList", "newsDetail", "transferNews", "socialPlayers"};
        for (String cacheName : caches) {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) cache.clear();
        }
        return caches;
    }
}
