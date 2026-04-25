package com.premierleague.server.health;

import com.premierleague.server.entity.FetchLog;
import com.premierleague.server.entity.SyncLog;
import com.premierleague.server.repository.FetchLogRepository;
import com.premierleague.server.repository.SyncLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 同步任务健康指示器
 *
 * 思路：定时任务真正的问题（上游 API 挂了、调度线程卡死、数据库连不上）
 * 现在只会打 ERROR 日志到 stdout 再写一条 sync_logs / fetch_log 失败记录，
 * 没有任何主动告警，运维只能靠肉眼看 /api/admin/stats 才知道"哦，一小时没抓到了"。
 *
 * 这里把失败/陈旧状态映射到 Spring Actuator 健康端点：
 *   GET /actuator/health  →  status=DOWN  →  容器编排的探针告警能接通
 *
 * 判断规则（全部 OR，命中任一就 DOWN）：
 *   1. sync_logs 里 STANDINGS 最近一条 > staleness.standings-minutes 分钟没更新（上游 API 挂了）
 *   2. sync_logs 里 MATCHES   最近一条 > staleness.matches-minutes   分钟没更新
 *   3. sync_logs 里该类型最近一条 status=FAILED
 *   4. fetch_log 最近 30 分钟 status=failed 的记录 ≥ fetch-failure-threshold 条（抓取源批量挂了）
 *
 * 重要取舍：
 *   - 冷启动容忍：sync_logs 表空 → 直接 UP，不误报。全新部署 / 重置 DB 后几分钟内都是这状态
 *   - 探针分层：docker-compose 的 liveness 探针切到 /actuator/health/liveness（只看 JVM 活着没），
 *     不再看本 indicator，避免"上游 football-data.org 抖一下 → 容器被重启"的雪崩
 *   - 外部可见性：本 indicator 只在 /actuator/health 里显示；
 *     外部 dead-man-switch（Healthchecks.io）靠 NewsFetchScheduler.hourlyReport() 的 ping 兜底
 */
@Slf4j
@Component("sync")
@RequiredArgsConstructor
public class SyncHealthIndicator implements HealthIndicator {

    private final SyncLogRepository syncLogRepository;
    private final FetchLogRepository fetchLogRepository;

    @Value("${app.sync-health.staleness.standings-minutes:15}")
    private long standingsStalenessMinutes;

    @Value("${app.sync-health.staleness.matches-minutes:10}")
    private long matchesStalenessMinutes;

    @Value("${app.sync-health.fetch-failure-threshold:5}")
    private int fetchFailureThreshold;

    @Value("${app.sync-health.fetch-failure-window-minutes:30}")
    private long fetchFailureWindowMinutes;

    @Override
    public Health health() {
        try {
            return doCheck();
        } catch (Exception e) {
            // indicator 本身不该把探针打挂 —— DB 临时故障这条路径会走到这里
            log.warn("[SyncHealth] Health check itself failed, reporting UNKNOWN", e);
            return Health.unknown()
                    .withDetail("error", "health check failed: " + e.getClass().getSimpleName())
                    .build();
        }
    }

    private Health doCheck() {
        List<String> problems = new ArrayList<>();
        Health.Builder details = Health.up();

        // --- 1/2. sync_logs 陈旧度 + 最近状态 ---
        checkSyncType("STANDINGS", standingsStalenessMinutes, problems, details);
        checkSyncType("MATCHES", matchesStalenessMinutes, problems, details);

        // --- 3. fetch_log 最近失败计数 ---
        LocalDateTime fetchSince = LocalDateTime.now().minusMinutes(fetchFailureWindowMinutes);
        List<FetchLog> recentFailures = fetchLogRepository.findByStatusAndCreatedAtAfter("failed", fetchSince);
        int failureCount = recentFailures.size();
        details.withDetail("fetchFailuresRecent", failureCount);
        details.withDetail("fetchFailureWindowMinutes", fetchFailureWindowMinutes);
        if (failureCount >= fetchFailureThreshold) {
            problems.add(String.format(
                    "fetch failures: %d in last %dmin (threshold=%d)",
                    failureCount, fetchFailureWindowMinutes, fetchFailureThreshold));
        }

        if (problems.isEmpty()) {
            return details.build();
        }
        return details.status(org.springframework.boot.actuate.health.Status.DOWN)
                .withDetail("problems", problems)
                .build();
    }

    /**
     * 检查一个 sync_type 最近一次同步的陈旧度和成功/失败状态
     * sync_logs 空 → 冷启动容忍，直接跳过（不报 problem，不加 detail 避免混淆）
     */
    private void checkSyncType(String syncType, long stalenessMinutes,
                                List<String> problems, Health.Builder details) {
        Optional<SyncLog> latest = syncLogRepository.findTopBySyncTypeOrderBySyncTimeDesc(syncType);
        if (latest.isEmpty()) {
            details.withDetail(syncType.toLowerCase() + "LastSync", "never");
            return;
        }
        SyncLog log = latest.get();
        long ageMinutes = Duration.between(log.getSyncTime(), LocalDateTime.now()).toMinutes();
        details.withDetail(syncType.toLowerCase() + "LastSync", log.getSyncTime().toString());
        details.withDetail(syncType.toLowerCase() + "LastStatus", log.getStatus());
        details.withDetail(syncType.toLowerCase() + "AgeMinutes", ageMinutes);

        if (ageMinutes > stalenessMinutes) {
            problems.add(String.format(
                    "%s stale: last sync %dmin ago (threshold=%dmin)",
                    syncType, ageMinutes, stalenessMinutes));
        }
        if ("FAILED".equalsIgnoreCase(log.getStatus())) {
            problems.add(String.format("%s last sync FAILED: %s",
                    syncType,
                    log.getErrorMessage() != null ? truncate(log.getErrorMessage(), 120) : "no error detail"));
        }
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }
}
