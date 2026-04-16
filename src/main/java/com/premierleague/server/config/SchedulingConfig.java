package com.premierleague.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 调度器配置
 * 启用 Spring 定时任务
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
