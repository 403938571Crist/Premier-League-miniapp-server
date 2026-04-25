package com.premierleague.server.scheduler;

import com.premierleague.server.service.NewsFetchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 资讯抓取定时任务
 *
 * 高频源：每2分钟（罗马诺、X）
 * 中频源：每10分钟（官方、懂球帝）
 * 低频源：每30分钟（B站、抖音）
 *
 * 故障可观测性：
 *   - 内部：每次失败写 fetch_log，SyncHealthIndicator 会把聚合状态反映到 /actuator/health
 *   - 外部：每小时向 Healthchecks.io（或兼容服务）ping 一次作为 dead-man-switch。
 *     只要这个 ping 停了超过配置的 grace period，Healthchecks 那边就会发邮件/短信。
 *     为什么靠 ping 不靠 push：ping 能捕捉"整个容器进程都挂了"的情况——
 *     容器挂了就发不出 ping，而发 push 的告警系统碰上容器挂了永远不会触发。
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class NewsFetchScheduler {

    private final NewsFetchService newsFetchService;

    @Value("${app.healthcheck.ping-url:}")
    private String healthcheckPingUrl;

    private static final HttpClient PING_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    /**
     * 高频源抓取 - 每2分钟
     * 罗马诺、X 等快讯源
     */
    @Scheduled(fixedRate = 2 * 60 * 1000) // 2分钟
    public void fetchHighFrequency() {
        log.info("[Scheduler] Triggering high frequency fetch");
        try {
            newsFetchService.fetchByFrequency("high");
        } catch (Exception e) {
            log.error("[Scheduler] High frequency fetch failed", e);
        }
    }

    /**
     * 中频源抓取 - 每10分钟
     * 官方、懂球帝等
     */
    @Scheduled(fixedRate = 10 * 60 * 1000) // 10分钟
    public void fetchMediumFrequency() {
        log.info("[Scheduler] Triggering medium frequency fetch");
        try {
            newsFetchService.fetchByFrequency("medium");
        } catch (Exception e) {
            log.error("[Scheduler] Medium frequency fetch failed", e);
        }
    }

    /**
     * 低频源抓取 - 每30分钟
     * B站、抖音等
     */
    @Scheduled(fixedRate = 30 * 60 * 1000) // 30分钟
    public void fetchLowFrequency() {
        log.info("[Scheduler] Triggering low frequency fetch");
        try {
            newsFetchService.fetchByFrequency("low");
        } catch (Exception e) {
            log.error("[Scheduler] Low frequency fetch failed", e);
        }
    }

    /**
     * 每小时心跳 —— 对外部 dead-man-switch 发 ping
     *
     * 约定：配置了 app.healthcheck.ping-url 才启用（典型值是 Healthchecks.io 的唯一 URL）。
     * 开发 / 本地环境留空 → 这里直接静默返回，不刷日志。
     *
     * 这个任务故意做得极简：
     *   - 不查 DB（查 DB 可能失败，但 DB 挂了不等于调度线程死了——那是 DB indicator 的事）
     *   - 不取统计（统计失败就发不出 ping，会造成"定时任务还好端端跑着但告警却响了"的误报）
     *   - 只要进程活着、调度线程还在转、网络还能出去，这个 ping 就能发出去
     */
    @Scheduled(cron = "0 0 * * * ?") // 每小时整点
    public void hourlyReport() {
        log.info("[Scheduler] Hourly heartbeat");

        if (healthcheckPingUrl == null || healthcheckPingUrl.isBlank()) {
            return; // 未配置 → 静默跳过
        }

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(healthcheckPingUrl))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<Void> resp = PING_CLIENT.send(req, HttpResponse.BodyHandlers.discarding());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                log.debug("[Scheduler] Healthcheck ping ok ({})", resp.statusCode());
            } else {
                log.warn("[Scheduler] Healthcheck ping returned non-2xx: {}", resp.statusCode());
            }
        } catch (Exception e) {
            // 告警系统挂了不应该让我们的调度线程出异常 —— 吞掉即可
            log.warn("[Scheduler] Healthcheck ping failed: {}", e.getMessage());
        }
    }
}
