package com.premierleague.server.service;

import com.premierleague.server.entity.News;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class NewsOriginalContentService {

    private static final Set<String> INLINE_CONTENT_SOURCES = Set.of("romano", "reddit", "x");
    private static final Pattern REDDIT_META_SUMMARY_PATTERN =
            Pattern.compile("^\\s*(?:\\[[^\\]]+\\]\\s*)?[↑^]?\\d+\\s*[·•|-]\\s*\\d+\\s*条评论\\s*$");

    public News ensureContent(News news) {
        if (!needsOriginalContent(news)) {
            return news;
        }

        String generated = buildContent(news);
        if (generated != null && !generated.isBlank()) {
            news.setContent(generated.trim());
        }
        return news;
    }

    public boolean needsOriginalContent(News news) {
        if (!supports(news)) {
            return false;
        }

        String content = safe(news.getContent());
        if (content.isBlank()) {
            return true;
        }

        return "reddit".equalsIgnoreCase(news.getSourceType())
                && isRedditMetaOnly(content)
                && !safe(news.getTitle()).isBlank();
    }

    private String buildContent(News news) {
        return switch (news.getSourceType().toLowerCase(Locale.ROOT)) {
            case "romano", "x" -> firstNonBlank(news.getContent(), news.getSummary(), news.getTitle());
            case "reddit" -> buildRedditContent(news);
            default -> news.getContent();
        };
    }

    private String buildRedditContent(News news) {
        String summary = safe(news.getSummary());
        if (!summary.isBlank() && !isRedditMetaOnly(summary)) {
            return summary;
        }
        return firstNonBlank(news.getTitle(), summary);
    }

    private boolean supports(News news) {
        return news != null
                && news.getSourceType() != null
                && INLINE_CONTENT_SOURCES.contains(news.getSourceType().toLowerCase(Locale.ROOT));
    }

    private boolean isRedditMetaOnly(String text) {
        return REDDIT_META_SUMMARY_PATTERN.matcher(safe(text)).matches();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
