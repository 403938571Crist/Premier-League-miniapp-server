package com.premierleague.server.model;

import java.util.List;

/**
 * 转会快讯 - 对应字段字典 7. 转会快讯字段
 * 用于 GET /api/news/transfers
 */
public record TransferNews(
        String id,
        String title,
        String summary,
        String source,
        String sourceType,
        String publishedAt,
        List<Long> relatedTeamIds,
        List<Long> relatedPlayerIds,
        Integer hotScore,
        String url
) {
}
