package com.premierleague.server.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 根路径健康检查
 * 微信云托管等容器平台默认会访问根路径检测服务存活状态
 */
@RestController
public class HealthController {

    @GetMapping("/")
    public Map<String, Object> root() {
        return Map.of(
            "status", "UP",
            "service", "premier-league-news-aggregator",
            "timestamp", System.currentTimeMillis()
        );
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "service", "premier-league-news-aggregator",
            "timestamp", System.currentTimeMillis()
        );
    }
}
