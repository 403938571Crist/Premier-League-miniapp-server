package com.premierleague.server.provider;

import com.premierleague.server.entity.News;

import java.util.List;

/**
 * 资讯源 Provider 接口
 * 每个数据源需要实现此接口
 */
public interface NewsProvider {
    
    /**
     * 获取来源类型标识
     */
    String getSourceType();
    
    /**
     * 获取来源名称
     */
    String getSourceName();
    
    /**
     * 抓取最新资讯
     * @param maxItems 最大抓取条数
     * @return 抓取到的资讯列表
     */
    List<News> fetchLatest(int maxItems);
    
    /**
     * 检查源是否可用
     */
    boolean isAvailable();
    
    /**
     * 获取抓取频率级别：high / medium / low
     */
    String getFrequencyLevel();
}
