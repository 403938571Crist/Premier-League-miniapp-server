package com.premierleague.server.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.premierleague.server.entity.News;
import com.premierleague.server.service.ContentCleanService;
import com.premierleague.server.util.HttpClientUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 懂球帝 资讯源 - 中频源
 * 抓取英超相关新闻
 * 
 * 【整改后】
 * - 栏目 ID 配置化，不再写死 56
 * - 时间字段优先级：created_at > sort_timestamp > published_at > now
 * - mediaType 改用 channel/showtype/is_video/template 判断
 * - 只做列表抓取，详情由 DongqiudiDetailService 补抓
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DongqiudiProvider implements NewsProvider {
    
    private final HttpClientUtil httpClient;
    private final ContentCleanService contentCleanService;
    private final ObjectMapper objectMapper;
    
    /**
     * 英超栏目 ID 配置化
     * 默认使用 3 (根据调研，3 是英超栏目)
     * 可通过配置覆盖
     */
    @Value("${app.sources.dongqiudi.premier-league-tab-id:3}")
    private String premierLeagueTabId;
    
    @Value("${app.sources.dongqiudi.base-url:https://www.dongqiudi.com/api/app/tabs/web}")
    private String baseUrl;
    
    // 时间解析格式
    private static final DateTimeFormatter DATE_TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    @Override
    public String getSourceType() {
        return "dongqiudi";
    }
    
    @Override
    public String getSourceName() {
        return "懂球帝";
    }
    
    @Override
    public List<News> fetchLatest(int maxItems) {
        String apiUrl = baseUrl + "/" + premierLeagueTabId + ".json";
        log.info("[Dongqiudi] Fetching from Premier League tab [{}]: {}", premierLeagueTabId, apiUrl);
        
        List<News> allNews = new ArrayList<>();
        int page = 1;
        int maxPages = 3;
        
        while (allNews.size() < maxItems && page <= maxPages) {
            try {
                String url = apiUrl + "?page=" + page;
                String response = httpClient.get(url);
                
                if (response == null || response.isEmpty()) {
                    log.warn("[Dongqiudi] Empty response from page {}", page);
                    break;
                }
                
                // 验证返回的是否是英超内容
                JsonNode root = objectMapper.readTree(response);
                String label = root.path("label").asText("");
                log.debug("[Dongqiudi] Tab label: {}", label);
                
                List<News> pageNews = parseResponse(root);
                
                // 过滤只保留英超相关内容
                List<News> plNews = pageNews.stream()
                    .filter(this::isPremierLeagueRelated)
                    .toList();
                
                allNews.addAll(plNews);
                
                log.info("[Dongqiudi] Page {} parsed {} items, {} PL related", 
                    page, pageNews.size(), plNews.size());
                
                if (pageNews.isEmpty()) {
                    break;
                }
                
                page++;
                Thread.sleep(500); // polite delay
                
            } catch (Exception e) {
                log.error("[Dongqiudi] Failed to fetch page {}", page, e);
                break;
            }
        }
        
        return allNews.stream().limit(maxItems).toList();
    }
    
    /**
     * 解析列表响应
     */
    private List<News> parseResponse(JsonNode root) {
        List<News> newsList = new ArrayList<>();
        
        try {
            JsonNode articles = root.path("articles");
            
            if (articles.isArray()) {
                for (JsonNode article : articles) {
                    News news = parseArticle(article);
                    if (news != null) {
                        newsList.add(news);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[Dongqiudi] Failed to parse response", e);
        }
        
        return newsList;
    }
    
    /**
     * 解析单篇文章 - 列表阶段轻解析
     * 字段抽取：id, title, description, b_description, thumb, created_at, sort_timestamp,
     *          channel, showtype, is_video, template, author_name, url
     */
    private News parseArticle(JsonNode article) {
        try {
            String id = article.path("id").asText();
            if (id.isEmpty()) {
                log.warn("[Dongqiudi] Article id is empty");
                return null;
            }
            
            String title = article.path("title").asText().trim();
            if (title.isEmpty()) {
                log.warn("[Dongqiudi] Article title is empty, id={}", id);
                return null;
            }
            
            // 摘要优先级：description > b_description > 空（详情再补）
            String description = article.path("description").asText("").trim();
            String bDescription = article.path("b_description").asText("").trim();
            String summary = !description.isEmpty() ? description : 
                            (!bDescription.isEmpty() ? bDescription : "");
            
            // 限制摘要长度
            if (!summary.isEmpty() && summary.length() > 2000) {
                summary = summary.substring(0, 2000);
            }
            
            // 时间优先级：created_at > sort_timestamp > published_at > now
            LocalDateTime publishTime = parsePublishTime(article);
            
            // 封面
            String cover = article.path("thumb").asText("");
            
            // 作者
            String author = article.path("author_name").asText("懂球帝");
            
            // URL
            String url = "https://www.dongqiudi.com/article/" + id;
            
            // 媒体类型判断（使用 channel/showtype/is_video/template）
            String mediaType = detectMediaType(article);
            
            // 过滤垃圾内容
            if (isGarbageContent(title, summary)) {
                log.debug("[Dongqiudi] Skipping garbage: {}", title.substring(0, Math.min(30, title.length())));
                return null;
            }
            
            News news = News.builder()
                    .id("dqd-" + id)
                    .title(title)
                    .summary(summary)
                    .source("懂球帝")
                    .sourceType("dongqiudi")
                    .mediaType(mediaType)
                    .sourcePublishedAt(publishTime)
                    .url(url)
                    .coverImage(cover)
                    .author(author)
                    .build();
            
            // 提取标签
            String extractedTags = contentCleanService.extractTags(title, summary);
            news.setTags(extractedTags);
            news.setHotScore(contentCleanService.calculateDefaultHotScore(news));
            
            log.debug("[Dongqiudi] Parsed article: id={}, title={}, mediaType={}, time={}", 
                id, title.substring(0, Math.min(40, title.length())), mediaType, publishTime);
            
            return news;
            
        } catch (Exception e) {
            log.error("[Dongqiudi] Failed to parse article: {}", article.path("id").asText(), e);
            return null;
        }
    }
    
    /**
     * 时间字段解析 - 优先级：created_at > sort_timestamp > published_at > now
     */
    private LocalDateTime parsePublishTime(JsonNode article) {
        LocalDateTime now = LocalDateTime.now();
        
        try {
            // 1. 优先使用 created_at
            String createdAt = article.path("created_at").asText("");
            if (!createdAt.isEmpty() && !createdAt.startsWith("20")) {
                // 可能是时间戳
                try {
                    long timestamp = article.path("created_at").asLong();
                    if (timestamp > 0) {
                        LocalDateTime parsed = LocalDateTime.ofEpochSecond(timestamp, 0, ZoneOffset.ofHours(8));
                        if (!isFutureTime(parsed, now)) {
                            return parsed;
                        }
                    }
                } catch (Exception ignored) {}
            } else if (!createdAt.isEmpty()) {
                try {
                    LocalDateTime parsed = LocalDateTime.parse(createdAt, DATE_TIME_FORMATTER);
                    if (!isFutureTime(parsed, now)) {
                        return parsed;
                    }
                } catch (Exception ignored) {}
            }
            
            // 2. 其次使用 sort_timestamp
            long sortTimestamp = article.path("sort_timestamp").asLong(0);
            if (sortTimestamp > 0) {
                LocalDateTime parsed = LocalDateTime.ofEpochSecond(sortTimestamp, 0, ZoneOffset.ofHours(8));
                if (!isFutureTime(parsed, now)) {
                    return parsed;
                }
            }
            
            // 3. 再次使用 published_at（需要校验）
            String publishedAt = article.path("published_at").asText("");
            if (!publishedAt.isEmpty() && publishedAt.contains("-")) {
                try {
                    LocalDateTime parsed = LocalDateTime.parse(publishedAt, DATE_TIME_FORMATTER);
                    if (!isFutureTime(parsed, now)) {
                        return parsed;
                    }
                } catch (Exception ignored) {}
            }
            
        } catch (Exception e) {
            log.warn("[Dongqiudi] Failed to parse time fields, using now");
        }
        
        return now;
    }
    
    /**
     * 判断时间是否是未来时间（超过当前 24 小时）
     */
    private boolean isFutureTime(LocalDateTime time, LocalDateTime now) {
        return time.isAfter(now.plusHours(24));
    }
    
    /**
     * 媒体类型判断 - 使用 channel/showtype/is_video/template
     */
    private String detectMediaType(JsonNode article) {
        // 1. 视频判断
        boolean isVideo = article.path("is_video").asBoolean(false);
        String channel = article.path("channel").asText("").toLowerCase();
        String showtype = article.path("showtype").asText("").toLowerCase();
        
        if (isVideo || channel.equals("video") || showtype.equals("video")) {
            return "video-summary";
        }
        
        // 2. 图集判断
        String template = article.path("template").asText("").toLowerCase();
        boolean hasImages = article.has("mini_top_content") && 
                           article.path("mini_top_content").has("images");
        
        if (showtype.equals("feed") || template.equals("top.html") || hasImages) {
            return "gallery";
        }
        
        // 3. 默认文章
        return "article";
    }
    
    /**
     * 英超过滤 - 只保留英超相关内容
     */
    private boolean isPremierLeagueRelated(News news) {
        String text = (news.getTitle() + " " + news.getSummary()).toLowerCase();
        
        String[] plKeywords = {
            "英超", "premier league",
            "阿森纳", "arsenal",
            "利物浦", "liverpool",
            "曼城", "manchester city", "man city",
            "曼联", "manchester united", "man utd", "man united",
            "切尔西", "chelsea",
            "热刺", "tottenham", "spurs",
            "纽卡", "纽卡斯尔", "newcastle",
            "布莱顿", "brighton",
            "维拉", "aston villa",
            "埃弗顿", "everton",
            "富勒姆", "fulham",
            "布伦特福德", "brentford",
            "伯恩茅斯", "bournemouth",
            "西汉姆", "west ham",
            "狼队", "wolves", "wolverhampton",
            "水晶宫", "crystal palace",
            "诺丁汉森林", "nottingham forest",
            "森林", "nottm forest",
            "莱斯特城", "leicester",
            "伊普斯维奇", "ipswich",
            "南安普顿", "southampton"
        };
        
        for (String keyword : plKeywords) {
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        
        log.debug("[Dongqiudi] Filtering out non-PL article: {}", 
            news.getTitle().substring(0, Math.min(40, news.getTitle().length())));
        return false;
    }
    
    /**
     * 只过滤明显的垃圾内容
     */
    private boolean isGarbageContent(String title, String summary) {
        String text = (title + " " + summary).toLowerCase();
        
        String[] garbageKeywords = {
            "点击领取", "扫码", "优惠券", "抽奖", "测试", "test",
            "点击进入", "下载APP", "关注公众号", "广告", "推广",
            "点击查看", "限时抢购"
        };
        
        for (String kw : garbageKeywords) {
            if (text.contains(kw.toLowerCase())) {
                return true;
            }
        }
        
        // 标题太短（少于 4 个字）可能是无效内容
        if (title.length() < 4) {
            return true;
        }
        
        return false;
    }
    
    @Override
    public boolean isAvailable() {
        try {
            String apiUrl = baseUrl + "/" + premierLeagueTabId + ".json?page=1";
            String response = httpClient.get(apiUrl);
            if (response == null || response.isEmpty()) {
                return false;
            }
            
            // 验证返回的是 JSON 且包含 articles
            JsonNode root = objectMapper.readTree(response);
            return root.has("articles");
            
        } catch (Exception e) {
            log.warn("[Dongqiudi] Availability check failed", e);
            return false;
        }
    }
    
    @Override
    public String getFrequencyLevel() {
        return "medium";
    }
    
    /**
     * 获取当前配置的英超栏目 ID
     */
    public String getCurrentTabId() {
        return premierLeagueTabId;
    }
}
