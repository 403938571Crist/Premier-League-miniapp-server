package com.premierleague.server.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.premierleague.server.entity.News;
import com.premierleague.server.service.ContentCleanService;
import com.premierleague.server.util.HttpClientUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
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
                
                // 记录响应结构便于调试
                JsonNode root = objectMapper.readTree(response);
                String label = root.path("label").asText("(no label)");
                // 打印顶层字段名，方便定位 articles 字段叫什么
                StringBuilder keys = new StringBuilder();
                root.fieldNames().forEachRemaining(k -> keys.append(k).append(","));
                log.info("[Dongqiudi] Tab={} label={} topKeys=[{}]", premierLeagueTabId, label, keys);
                
                List<News> pageNews = parseResponse(root);
                
                // 过滤只保留英超相关内容
                List<News> plNews = pageNews.stream()
                    .filter(this::isPremierLeagueRelated)
                    .toList();

                // 补抓正文（只对通过过滤的文章）
                for (News news : plNews) {
                    try {
                        String articleId = news.getId().replace("dqd-", "");
                        String content = fetchArticleContent(articleId);
                        if (content != null && !content.isEmpty()) {
                            news.setContent(content);
                        }
                        Thread.sleep(300);
                    } catch (Exception e) {
                        log.warn("[Dongqiudi] Failed to fetch content for {}", news.getId());
                    }
                }

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
     * 懂球帝不同版本/接口的列表字段名不固定，按优先级依次尝试
     */
    private List<News> parseResponse(JsonNode root) {
        List<News> newsList = new ArrayList<>();

        try {
            // 尝试多个可能的列表字段名
            JsonNode articles = findArticlesNode(root);

            if (articles == null || !articles.isArray()) {
                log.warn("[Dongqiudi] No articles node found. Response keys: {}",
                        root.fieldNames().hasNext() ? root.fieldNames().next() : "(empty)");
                return newsList;
            }

            log.debug("[Dongqiudi] Found {} raw articles", articles.size());
            for (JsonNode article : articles) {
                News news = parseArticle(article);
                if (news != null) {
                    newsList.add(news);
                }
            }
        } catch (Exception e) {
            log.error("[Dongqiudi] Failed to parse response", e);
        }

        return newsList;
    }

    /**
     * 按优先级查找文章列表节点，兼容不同 API 版本
     */
    private JsonNode findArticlesNode(JsonNode root) {
        for (String fieldName : new String[]{"articles", "list", "data", "feeds", "items"}) {
            JsonNode node = root.path(fieldName);
            if (node.isArray() && !node.isEmpty()) {
                log.debug("[Dongqiudi] Using '{}' as articles field", fieldName);
                return node;
            }
        }
        // 最后兜底：root 本身就是数组
        if (root.isArray() && !root.isEmpty()) {
            log.debug("[Dongqiudi] Root itself is the articles array");
            return root;
        }
        return null;
    }
    
    /**
     * 解析单篇文章 - 列表阶段轻解析
     * 懂球帝 CDN 的 URL 路径里带尺寸，如 `/280x210/crop/-/` 或 `/200x150/crop/-/`。
     * 经测试该 CDN 支持任意尺寸，返回 720x540 可以避免卡片里的上采样模糊。
     * 不匹配小尺寸模式就原样返回，不破坏非 DQD CDN 的 URL。
     */
    private static final java.util.regex.Pattern DQD_SIZE_PATTERN =
            java.util.regex.Pattern.compile("/\\d{2,4}x\\d{2,4}/");

    private String upgradeDqdImageSize(String url) {
        if (url == null || url.isEmpty()) return url;
        if (!url.contains("bdimg") && !url.contains("qunliao") && !url.contains("fastdfs")) {
            return url;
        }
        java.util.regex.Matcher m = DQD_SIZE_PATTERN.matcher(url);
        if (!m.find()) return url;
        return m.replaceFirst("/720x540/");
    }

    /*
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
            
            // 封面（把 DQD CDN 默认的 280x210 缩略图升级成 720x540，小程序卡片宽度 ≈750px，
            // 原图显示时会 2.7x 上采样非常糊；720x540 刚好对齐 2x retina 宽度）
            String cover = upgradeDqdImageSize(article.path("thumb").asText(""));
            
            // 作者
            String author = article.path("author_name").asText("懂球帝");
            
            // URL：优先用 API 返回的 H5 链接，降级构造
            String apiUrl1 = article.path("url1").asText("");
            String apiUrl = article.path("url").asText("");
            String url = !apiUrl1.isEmpty() ? apiUrl1 :
                         (!apiUrl.isEmpty() ? apiUrl :
                         "https://n.dongqiudi.com/webapp/news.html?articleId=" + id);
            
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
        // 不做实时 HTTP 探测，fetchLatest() 内部有完整容错
        return true;
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

    /**
     * 抓取懂球帝 PC 版文章正文
     * URL: https://www.dongqiudi.com/articles/{articleId}.html
     *
     * 结构：<div class="con"><div style="display:none;"> 段落 + 图片
     * 返回格式：段落用 \n\n 分隔，图片用 [IMG:url] 占位
     */
    public String fetchArticleContent(String articleId) {
        String url = "https://www.dongqiudi.com/articles/" + articleId + ".html";
        try {
            String html = httpClient.get(url);
            if (html == null || html.isEmpty()) {
                log.warn("[Dongqiudi] Empty HTML for article {}", articleId);
                return null;
            }

            Document doc = Jsoup.parse(html);

            // 定位正文容器：<div class="con"> 下的第一个隐藏 div
            Element conDiv = doc.selectFirst("div.con");
            if (conDiv == null) {
                log.debug("[Dongqiudi] No .con div found for article {}", articleId);
                return null;
            }

            // 隐藏的 SEO 快照 div 包含完整正文
            Element contentDiv = conDiv.selectFirst("div[style*=display:none]");
            if (contentDiv == null) {
                // 降级：直接用 .con 内容
                contentDiv = conDiv;
            }

            List<String> blocks = new ArrayList<>();
            for (Element child : contentDiv.children()) {
                String tag = child.tagName().toLowerCase();

                if (tag.equals("p")) {
                    // 段落文本
                    String text = child.text().trim();
                    if (!text.isEmpty()) {
                        blocks.add(text);
                    }
                    // 段落内的图片
                    for (Element img : child.select("img")) {
                        String imgUrl = resolveImgUrl(img);
                        if (imgUrl != null) blocks.add("[IMG:" + imgUrl + "]");
                    }
                } else if (tag.equals("img")) {
                    String imgUrl = resolveImgUrl(child);
                    if (imgUrl != null) blocks.add("[IMG:" + imgUrl + "]");
                } else if (tag.equals("div") || tag.equals("section")) {
                    // 嵌套 div 里的段落
                    for (Element p : child.select("p")) {
                        String text = p.text().trim();
                        if (!text.isEmpty()) blocks.add(text);
                    }
                    for (Element img : child.select("img")) {
                        String imgUrl = resolveImgUrl(img);
                        if (imgUrl != null) blocks.add("[IMG:" + imgUrl + "]");
                    }
                }
            }

            if (blocks.isEmpty()) {
                log.debug("[Dongqiudi] No content blocks for article {}", articleId);
                return null;
            }

            String content = String.join("\n\n", blocks);
            log.info("[Dongqiudi] Fetched content for article {}: {} blocks, {} chars",
                    articleId, blocks.size(), content.length());
            return content;

        } catch (Exception e) {
            log.warn("[Dongqiudi] Failed to fetch article {}: {}", articleId, e.getMessage());
            return null;
        }
    }

    private String resolveImgUrl(Element img) {
        // 优先 orig-src（原图），其次 data-src（懒加载），最后 src
        for (String attr : new String[]{"orig-src", "data-src", "src"}) {
            String val = img.attr(attr).trim();
            if (!val.isEmpty() && val.startsWith("http")) {
                return val;
            }
        }
        return null;
    }
}
