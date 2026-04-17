package com.premierleague.server.provider;

import com.premierleague.server.entity.News;
import com.premierleague.server.service.ContentCleanService;
import com.premierleague.server.util.HttpClientUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
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
            // 直播吧用 GBK 编码
            byte[] bytes = httpClient.getBytes(LIST_URL);
            if (bytes == null || bytes.length == 0) {
                log.warn("[Zhibo8] Empty response from list page");
                return result;
            }
            String html = new String(bytes, Charset.forName("GBK"));

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
                // 标题过滤：只保留英超相关
                if (isPremierLeagueTitle(title)) {
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
            String html = new String(bytes, Charset.forName("GBK"));

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

            // 封面图（第一张 tu.duoduocdn.com 图片）
            String coverImage = "";
            Matcher imgM = Pattern.compile("src=\"(//tu\\.duoduocdn\\.com/[^\"]+)\"").matcher(html);
            if (imgM.find()) {
                coverImage = "https:" + imgM.group(1);
            }

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
