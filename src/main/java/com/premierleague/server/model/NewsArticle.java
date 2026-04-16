package com.premierleague.server.model;

import java.util.List;

/**
 * 资讯文章 - 对应字段字典 4. 资讯详情字段
 */
public record NewsArticle(
        // 核心字段
        String id,
        String title,
        String summary,
        String source,
        String sourceType,
        String mediaType,
        String publishedAt,
        
        // 可选字段
        String author,
        String coverImage,
        List<String> tags,
        List<Long> relatedTeamIds,
        List<Long> relatedPlayerIds,
        Integer hotScore,
        String url,
        String sourceNote,
        
        // 详情页特有
        List<ArticleBlock> blocks
) {
    /**
     * 转换为列表项（去掉 blocks 等详情字段）
     */
    public NewsListItem toListItem() {
        return new NewsListItem(
                id, title, summary, source, sourceType, mediaType,
                publishedAt, coverImage, tags, hotScore, author, url
        );
    }
}
