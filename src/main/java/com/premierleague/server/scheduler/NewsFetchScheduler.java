package com.premierleague.server.scheduler;

import com.premierleague.server.service.NewsFetchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 资讯抓取定时任务
 * 
 * 高频源：每2分钟（罗马诺、X）
 * 中频源：每10分钟（官方、懂球帝）
 * 低频源：每30分钟（B站、抖音）
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class NewsFetchScheduler {
    
    private final NewsFetchService newsFetchService;
    
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
     * 每小时统计报告
     */
    @Scheduled(cron = "0 0 * * * ?") // 每小时整点
    public void hourlyReport() {
        log.info("[Scheduler] Hourly fetch report");
        // TODO: 可以发送统计报告到监控/告警系统
    }
}
