package com.premierleague.server.provider;

import com.premierleague.server.entity.News;
import com.premierleague.server.service.ContentCleanService;
import com.premierleague.server.util.HttpClientUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 直播吧 资讯源 - 中频源
 * 抓取足球新闻列表，过滤英超相关内容
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Zhibo8Provider implements NewsProvider {

    private final HttpClientUtil httpClient;
    private final ContentCleanService contentCleanService;

    private static final String LIST_URL = "https://news.zhibo8.com/zuqiu/";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 垃圾/博彩类标题黑名单 —— 命中任一关键词直接跳过。
     * 注意：必须在 PL_KEYWORDS 正向过滤之前执行！
     */
    private static final String[] BLOCKED_KEYWORDS = {
        "彩经", "竞彩", "足彩", "彩票", "赔率",
        "让球", "大小球", "欧赔", "亚盘", "胜平负",
        "博彩", "赌球", "投注", "返水", "对阵分析预测",
        "比分预测", "情报预测"
    };

    // 英超相关关键词（用于标题过滤，避免逐篇抓详情）
    private static final String[] PL_KEYWORDS = {
        "英超", "Premier League", "premier league",
        "阿森纳", "Arsenal", "arsenal",
        "利物浦", "Liverpool", "liverpool",
        "曼城", "Manchester City", "man city",
        "曼联", "Manchester United", "man utd",
        "切尔西", "Chelsea", "chelsea",
        "热刺", "Tottenham", "tottenham", "spurs",
        "纽卡", "Newcastle", "newcastle",
        "布莱顿", "Brighton", "brighton",
        "维拉", "Aston Villa", "aston villa",
        "埃弗顿", "Everton", "everton",
        "富勒姆", "Fulham", "fulham",
        "布伦特福德", "Brentford", "brentford",
        "伯恩茅斯", "Bournemouth", "bournemouth",
        "西汉姆", "West Ham", "west ham",
        "狼队", "Wolves", "wolverhampton",
        "水晶宫", "Crystal Palace", "crystal palace",
        "诺丁汉", "Nottingham", "nottingham",
        "莱斯特", "Leicester", "leicester",
        "伊普斯维奇", "Ipswich", "ipswich",
        "南安普顿", "Southampton", "southampton"
    };

    @Override
    public String getSourceType() {
        return "zhibo8";
    }

    @Override
    public String getSourceName() {
        return "直播吧";
    }

    @Override
    public String getFrequencyLevel() {
        return "medium";
    }

    @Override
    public List<News> fetchLatest(int maxItems) {
        log.info("[Zhibo8] Fetching football news list");
        List<News> result = new ArrayList<>();

        try {
            // 直播吧原来是 GBK，现已迁到 UTF-8；按内容嗅探，避免 mojibake 把中文标题打成乱码
            // 命中不了 PL 关键字（最后表现为 "Found 0 PL-related candidates"）
            byte[] bytes = httpClient.getBytes(LIST_URL);
            if (bytes == null || bytes.length == 0) {
                log.warn("[Zhibo8] Empty response from list page");
                return result;
            }
            String html = decodeHtml(bytes);

            // 提取文章链接和标题
            Pattern linkPattern = Pattern.compile(
                "href=\"(//news\\.zhibo8\\.(?:com|cc)/zuqiu/[^\"]+\\.htm)\"[^>]*>\\s*([^<]{5,120})"
            );
            Matcher m = linkPattern.matcher(html);

            List<String[]> candidates = new ArrayList<>();
            while (m.find() && candidates.size() < 200) {
                String url = "https:" + m.group(1);
                String title = m.group(2).trim();
                // 跳过导航链接
                if (url.contains("more.htm") || title.length() < 5) continue;
                // 先过滤博彩/垃圾内容，再判断是否英超相关
                if (!isSpamTitle(title) && isPremierLeagueTitle(title)) {
                    candidates.add(new String[]{url, title});
                }
            }

            log.info("[Zhibo8] Found {} PL-related candidates", candidates.size());

            for (String[] candidate : candidates) {
                if (result.size() >= maxItems) break;
                try {
                    News news = fetchArticle(candidate[0], candidate[1]);
                    if (news != null) {
                        result.add(news);
                    }
                    Thread.sleep(300);
                } catch (Exception e) {
                    log.warn("[Zhibo8] Failed to fetch article: {}", candidate[0], e);
                }
            }

        } catch (Exception e) {
            log.error("[Zhibo8] Failed to fetch list", e);
        }

        log.info("[Zhibo8] Fetched {} PL articles", result.size());
        return result;
    }

    private News fetchArticle(String url, String titleHint) {
        try {
            byte[] bytes = httpClient.getBytes(url);
            if (bytes == null || bytes.length == 0) return null;
            String html = decodeHtml(bytes);

            // 标题
            String title = extractTag(html, "title").replace("-直播吧", "").replace("-直播吧新闻", "").trim();
            if (title.isEmpty()) title = titleHint;

            // 时间
            LocalDateTime publishTime = LocalDateTime.now();
            Matcher tm = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2})").matcher(html);
            if (tm.find()) {
                try {
                    publishTime = LocalDateTime.parse(tm.group(1), DATE_FMT);
                } catch (Exception ignored) {}
            }

            // 正文（class="content"）
            String content = extractContent(html);

            // 封面图：优先从正文 <div class="content"> 区间内选第一张非装饰图，
            // 兜底再看全页。排除 static4style / logo / icon / 表情包等杂图。
            String coverImage = pickCoverImage(html);

            // 指纹：用 URL 生成
            String id = "zhibo8-" + url.replaceAll(".*/([^/]+)\\.htm.*", "$1");

            News news = News.builder()
                    .id(id)
                    .title(title)
                    .summary(content.length() > 300 ? content.substring(0, 300) + "..." : content)
                    .content(content)
                    .source("直播吧")
                    .sourceType("zhibo8")
                    .mediaType("article")
                    .sourcePublishedAt(publishTime)
                    .url(url)
                    .coverImage(coverImage)
                    .author("")
                    .build();

            String tags = contentCleanService.extractTags(title, content);
            news.setTags(tags);
            news.setHotScore(contentCleanService.calculateDefaultHotScore(news));
            news.setFingerprint(id);

            log.debug("[Zhibo8] Parsed: {} | {}", publishTime, title.substring(0, Math.min(40, title.length())));
            return news;

        } catch (Exception e) {
            log.error("[Zhibo8] Failed to parse article: {}", url, e);
            return null;
        }
    }

    /**
     * 从 HTML 字节里嗅探 charset：先看 &lt;meta charset=...&gt; / content="...charset=..."，
     * 命中 utf-8 就按 UTF-8 解；否则退回 GBK（兼容历史页面）。
     */
    private String decodeHtml(byte[] bytes) {
        // 只看前 2KB 足够覆盖 <head> 里的 meta
        String head = new String(bytes, 0, Math.min(bytes.length, 2048), StandardCharsets.ISO_8859_1)
                .toLowerCase(java.util.Locale.ROOT);
        if (head.contains("charset=\"utf-8\"") || head.contains("charset=utf-8")
                || head.contains("charset='utf-8'")) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return new String(bytes, Charset.forName("GBK"));
    }

    /**
     * 从文章 HTML 里挑一张靠谱的封面图：
     *   1) 优先正文 <div class="content"> 区间内；
     *   2) 只接受 *.duoduocdn.com 的图片 URL，忽略 static4style / logo / emoji 等；
     *   3) 再不行退回全页第一张可用图。
     * 找不到返回空串，由前端降级成 sourceType 主题色封面。
     */
    private String pickCoverImage(String html) {
        // 定位正文块；正则 . 默认不匹配换行，用 DOTALL
        Matcher contentM = Pattern.compile(
                "class=\"content\"[\\s\\S]*?</div>",
                Pattern.DOTALL).matcher(html);
        String contentHtml = contentM.find() ? contentM.group() : null;

        String inContent = findFirstImage(contentHtml);
        if (!inContent.isEmpty()) return inContent;

        String anywhere = findFirstImage(html);
        return anywhere;
    }

    private static final Pattern IMG_PATTERN = Pattern.compile(
            "src=\"(//[a-z0-9.-]*duoduocdn\\.com/[^\"]+?\\.(?:jpg|jpeg|png|gif|webp))\"",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SKIP_HINTS = Pattern.compile(
            "static4style|/logo|/icon|/ico/|/smile|/emoji|/face/|_btn|_bt_\\.|/ad/",
            Pattern.CASE_INSENSITIVE);

    private String findFirstImage(String fragment) {
        if (fragment == null || fragment.isEmpty()) return "";
        Matcher m = IMG_PATTERN.matcher(fragment);
        while (m.find()) {
            String url = m.group(1);
            if (SKIP_HINTS.matcher(url).find()) continue;
            return "https:" + url;
        }
        return "";
    }

    private String extractContent(String html) {
        // 找 class="content" div
        Matcher m = Pattern.compile("class=\"content\"[^>]*>(.*?)</div>", Pattern.DOTALL).matcher(html);
        if (m.find()) {
            String raw = m.group(1);
            // 去掉 HTML 标签，保留段落换行
            raw = raw.replaceAll("<p[^>]*>", "\n").replaceAll("</p>", "").replaceAll("<[^>]+>", " ");
            return raw.replaceAll("\\s{2,}", " ").trim();
        }
        return "";
    }

    private String extractTag(String html, String tag) {
        Matcher m = Pattern.compile("<" + tag + "[^>]*>([^<]+)</" + tag + ">", Pattern.CASE_INSENSITIVE).matcher(html);
        return m.find() ? m.group(1).trim() : "";
    }

    /** 命中任一博彩/垃圾关键词 → true（应丢弃） */
    private boolean isSpamTitle(String title) {
        for (String kw : BLOCKED_KEYWORDS) {
            if (title.contains(kw)) return true;
        }
        return false;
    }

    private boolean isPremierLeagueTitle(String title) {
        for (String kw : PL_KEYWORDS) {
            if (title.contains(kw)) return true;
        }
        return false;
    }

    @Override
    public boolean isAvailable() {
        try {
            byte[] bytes = httpClient.getBytes(LIST_URL);
            return bytes != null && bytes.length > 1000;
        } catch (Exception e) {
            return false;
        }
    }
}
