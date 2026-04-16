package com.premierleague.server.config.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 抓取源配置
 * 高频源：罗马诺/X - 每2分钟
 * 中频源：官方/懂球帝 - 每10分钟
 * 低频源：B站/抖音 - 每30分钟
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.fetch")
public class FetchSourceProperties {
    
    /**
     * 高频源配置
     */
    private SourceConfig highFrequency = new SourceConfig(
            List.of("romano", "x"),
            2,  // 2分钟
            100 // 每次最多抓100条
    );
    
    /**
     * 中频源配置
     */
    private SourceConfig mediumFrequency = new SourceConfig(
            List.of("official", "dongqiudi"),
            10, // 10分钟
            50
    );
    
    public List<String> getAllSources() {
        List<String> all = new ArrayList<>();
        all.addAll(highFrequency.getSources());
        all.addAll(mediumFrequency.getSources());
        all.addAll(lowFrequency.getSources());
        return all;
    }
    
    /**
     * 低频源配置
     */
    private SourceConfig lowFrequency = new SourceConfig(
            List.of("bilibili", "douyin"),
            30, // 30分钟
            30
    );
    
    @Data
    public static class SourceConfig {
        private List<String> sources;
        private int intervalMinutes;
        private int maxItemsPerFetch;
        
        public SourceConfig() {}
        
        public SourceConfig(List<String> sources, int intervalMinutes, int maxItemsPerFetch) {
            this.sources = sources;
            this.intervalMinutes = intervalMinutes;
            this.maxItemsPerFetch = maxItemsPerFetch;
        }
    }
}
