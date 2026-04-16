package com.premierleague.server.model;

import java.util.List;

/**
 * 资讯列表项 - 对应字段字典 3. 资讯列表项字段
 * 用于 GET /api/news 返回
 */
public record NewsListItem(
        String id,
        String title,
        String summary,
        String source,
        String sourceType,
        String mediaType,
        String publishedAt,
        String coverImage,
        List<String> tags,
        Integer hotScore,
        String author,
        String url
) {
}
