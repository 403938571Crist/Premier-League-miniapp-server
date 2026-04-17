package com.premierleague.server.service;

import com.premierleague.server.entity.News;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * 内容清洗服务
 * 对抓取的内容进行清洗、标准化处理
 */
@Slf4j
@Service
public class ContentCleanService {
    
    // HTML标签正则
    private static final Pattern HTML_PATTERN = Pattern.compile("<[^>]+>");
    // 多余空格正则
    private static final Pattern EXTRA_SPACE_PATTERN = Pattern.compile("\\s+");
    // 特殊字符正则
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile("[\\n\\r\\t]");
    
    /**
     * 清洗资讯内容
     */
    public News cleanNews(News news) {
        if (news == null) return null;
        
        // 清洗标题
        news.setTitle(cleanText(news.getTitle(), 500));
        
        // 清洗摘要
        news.setSummary(cleanText(news.getSummary(), 2000));
        
        // 清洗正文
        if (news.getContent() != null) {
            news.setContent(cleanContent(news.getContent()));
        }
        
        // 清洗作者
        if (news.getAuthor() != null) {
            news.setAuthor(cleanText(news.getAuthor(), 100));
        }
        
        // 标准化标签
        if (news.getTags() != null) {
            news.setTags(normalizeTags(news.getTags()));
        }
        
        // 设置默认值
        if (news.getHotScore() == null) {
            news.setHotScore(calculateDefaultHotScore(news));
        }
        
        if (news.getMediaType() == null || news.getMediaType().isEmpty()) {
            news.setMediaType(detectMediaType(news));
        }
        
        return news;
    }
    
    /**
     * 清洗文本
     * @param text 原始文本
     * @param maxLength 最大长度
     */
    public String cleanText(String text, int maxLength) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        // 去除HTML标签
        String cleaned = HTML_PATTERN.matcher(text).replaceAll("");
        
        // 替换特殊字符为空格
        cleaned = SPECIAL_CHAR_PATTERN.matcher(cleaned).replaceAll(" ");
        
        // 合并多余空格
        cleaned = EXTRA_SPACE_PATTERN.matcher(cleaned).replaceAll(" ");
        
        // 去除首尾空格
        cleaned = cleaned.trim();
        
        // 截断超长文本
        if (cleaned.length() > maxLength) {
            cleaned = cleaned.substring(0, maxLength - 3) + "...";
        }
        
        return cleaned;
    }
    
    /**
     * 清洗正文内容
     */
    public String cleanContent(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        
        // 保留段落格式，但清理HTML
        String cleaned = HTML_PATTERN.matcher(content).replaceAll("");
        
        // 标准化换行
        cleaned = cleaned.replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n");
        
        // 去除多余空行
        cleaned = cleaned.replaceAll("\n{3,}", "\n\n");
        
        return cleaned.trim();
    }
    
    /**
     * 标准化标签
     * 将标签统一为小写，去除多余空格
     */
    public String normalizeTags(String tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        
        return java.util.Arrays.stream(tags.split("[,，]"))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .map(String::toLowerCase)
                .distinct()
                .limit(10) // 最多10个标签
                .collect(java.util.stream.Collectors.joining(","));
    }
    
    /**
     * 检测媒体类型
     */
    public String detectMediaType(News news) {
        String content = news.getContent();
        String title = news.getTitle();
        
        // 根据标题关键词判断
        if (title != null) {
            String lowerTitle = title.toLowerCase();
            if (lowerTitle.contains("转会") || lowerTitle.contains("签约") || lowerTitle.contains("here we go")) {
                return "transfer";
            }
            if (lowerTitle.contains("视频") || lowerTitle.contains("集锦") || lowerTitle.contains("录像")) {
                return "video-summary";
            }
        }
        
        // 根据内容长度判断
        if (content != null && content.length() < 200) {
            return "quick"; // 短内容/快讯
        }
        
        // 根据来源判断
        String sourceType = news.getSourceType();
        if ("x".equals(sourceType) || "weibo".equals(sourceType)) {
            return "social";
        }
        if ("bilibili".equals(sourceType) || "douyin".equals(sourceType)) {
            return "video-summary";
        }
        
        return "article"; // 默认图文
    }
    
    /**
     * 计算默认热度值
     */
    public Integer calculateDefaultHotScore(News news) {
        int score = 50; // 基础分
        
        // 根据来源加权
        String sourceType = news.getSourceType();
        if ("romano".equals(sourceType)) score += 30;
        else if ("official".equals(sourceType)) score += 20;
        else if ("sky".equals(sourceType)) score += 18;
        else if ("guardian".equals(sourceType)) score += 15;
        else if ("x".equals(sourceType)) score += 15;
        else if ("reddit".equals(sourceType)) score += 5; // Reddit 热度由 RedditProvider 自己计算，此处仅兜底
        
        // 根据媒体类型加权
        String mediaType = news.getMediaType();
        if ("transfer".equals(mediaType)) score += 20;
        else if ("quick".equals(mediaType)) score += 10;
        
        // 根据标题关键词
        String title = news.getTitle();
        if (title != null) {
            String lowerTitle = title.toLowerCase();
            if (lowerTitle.contains("here we go")) score += 15;
            if (lowerTitle.contains("热刺") || lowerTitle.contains("阿森纳") || 
                lowerTitle.contains("曼联") || lowerTitle.contains("利物浦") || 
                lowerTitle.contains("曼城") || lowerTitle.contains("切尔西")) {
                score += 10;
            }
        }
        
        return Math.min(score, 100); // 最高100分
    }
    
    /**
     * 提取标签
     * 从标题和内容中自动提取标签
     */
    public String extractTags(String title, String content) {
        java.util.Set<String> tags = new java.util.HashSet<>();
        
        // 球队关键词
        String[] teams = {"阿森纳", "曼城", "利物浦", "曼联", "切尔西", "热刺", "纽卡斯尔", 
                         "布莱顿", "维拉", "西汉姆", "水晶宫", "布伦特福德", "富勒姆",
                         "埃弗顿", "森林", "伯恩茅斯", "狼队", "伯恩利", "卢顿", "谢菲联"};
        
        String text = (title + " " + (content != null ? content : "")).toLowerCase();
        
        for (String team : teams) {
            if (text.contains(team.toLowerCase())) {
                tags.add(team);
            }
        }
        
        // 类型关键词
        String[] types = {"转会", "续约", "伤病", "战术", "裁判", "VAR", "红牌", "黄牌",
                         "进球", "助攻", "帽子戏法", "乌龙球", "点球", "任意球"};
        
        for (String type : types) {
            if (text.contains(type)) {
                tags.add(type);
            }
        }
        
        return String.join(",", tags);
    }
}
