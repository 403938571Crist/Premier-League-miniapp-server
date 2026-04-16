package com.premierleague.server.model;

import java.util.List;

/**
 * 正文块 - 用于资讯详情
 * 对应字段字典 5. ArticleBlock 字段
 */
public record ArticleBlock(
        /**
         * 正文块类型: paragraph | quote | bullet
         */
        String type,
        
        /**
         * 段落或引用内容
         */
        String text,
        
        /**
         * 列表项（type=bullet时使用）
         */
        List<String> items
) {
    // 工厂方法
    public static ArticleBlock paragraph(String text) {
        return new ArticleBlock("paragraph", text, null);
    }
    
    public static ArticleBlock quote(String text) {
        return new ArticleBlock("quote", text, null);
    }
    
    public static ArticleBlock bullet(List<String> items) {
        return new ArticleBlock("bullet", null, items);
    }
}
