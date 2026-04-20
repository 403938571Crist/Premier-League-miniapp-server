package com.premierleague.server.service;

import com.premierleague.server.dto.PageResult;
import com.premierleague.server.entity.News;
import com.premierleague.server.model.ArticleBlock;
import com.premierleague.server.model.NewsArticle;
import com.premierleague.server.model.NewsListItem;
import com.premierleague.server.model.TransferNews;
import com.premierleague.server.repository.NewsRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = "news")
public class NewsService {

    private static final List<String> BLOCKED_SOURCE_TYPES = List.of("bilibili", "douyin");
    private static final Set<String> BLOCKED_NEWS_KEYWORDS = Set.of(
            
            
            
            
            
            
            
            
            
            
            
            
            
            
            
            "betting",
            "odds",
            "handicap",
            "tipster"
    );
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int FEATURED_FEED_MAX_PAGE = 5;
    private static final int FEATURED_FEED_MIN_WINDOW = 120;
    private static final int FEATURED_FEED_WINDOW_MULTIPLIER = 6;
    private static final int FEATURED_FEED_OTHER_BATCH_SIZE = 3;
    private static final int FEATURED_FEED_SOURCE_BATCH_SIZE = 24;
    private static final String SOURCE_OFFICIAL = "official";
    private static final String SOURCE_SKY = "sky";
    private static final String SOURCE_GUARDIAN = "guardian";
    private static final String SOURCE_ROMANO = "romano";
    private static final String SOURCE_REDDIT = "reddit";

    private final NewsRepository newsRepository;
    private final NewsImageService newsImageService;

    @Cacheable(value = "newsList", key = "#page+'-'+#pageSize+'-'+#sourceType+'-'+#tag+'-'+#keyword")
    public PageResult<NewsListItem> getNewsList(
            int page,
            int pageSize,
            String sourceType,
            String tag,
            String keyword
    ) {
        int safePage = Math.max(page, DEFAULT_PAGE);
        int requestedPageSize = pageSize <= 0 ? DEFAULT_PAGE_SIZE : pageSize;
        int safePageSize = Math.max(1, Math.min(requestedPageSize, MAX_PAGE_SIZE));

        log.debug("[NewsService] Getting news list from DB: page={}, size={}", safePage, safePageSize);

        Specification<News> spec = visibleNewsSpec(sourceType, tag, keyword, true);
        if (shouldBlendFeaturedSources(safePage, sourceType, tag, keyword)) {
            return getBlendedNewsList(safePage, safePageSize, tag, keyword);
        }

        Pageable pageable = PageRequest.of(safePage - 1, safePageSize);
        Page<News> newsPage = newsRepository.findAll(spec, pageable);

        List<NewsListItem> items = newsPage.getContent().stream()
                .map(this::convertToListItem)
                .collect(Collectors.toList());

        log.debug("[NewsService] Returning {} items from DB", items.size());
        return PageResult.of(items, safePage, safePageSize, newsPage.getTotalElements());
    }

    @Cacheable(value = "newsDetail", key = "#id")
    public Optional<NewsArticle> getNewsDetail(String id) {
        log.debug("[NewsService] Getting news detail from DB: id={}", id);

        return newsRepository.findById(id)
                .filter(this::isAllowedNews)
                .map(this::convertToArticle);
    }

    @Cacheable(value = "transferNews", key = "#source+'-'+#teamId+'-'+#playerId")
    public List<TransferNews> getTransferNews(String source, Long teamId, Long playerId) {
        log.debug("[NewsService] Getting transfer news from DB");

        List<News> transfers = newsRepository.findTransferNews("转会");

        return transfers.stream()
                .filter(item -> source == null || item.getSourceType().equals(source))
                .filter(item -> teamId == null
                        || (item.getRelatedTeamIds() != null && item.getRelatedTeamIds().contains(teamId.toString())))
                .filter(item -> playerId == null
                        || (item.getRelatedPlayerIds() != null && item.getRelatedPlayerIds().contains(playerId.toString())))
                .filter(this::isAllowedNews)
                .map(this::convertToTransferNews)
                .collect(Collectors.toList());
    }

    private NewsListItem convertToListItem(News news) {
        return new NewsListItem(
                news.getId(),
                news.getTitle(),
                news.getSummary(),
                news.getSource(),
                news.getSourceType(),
                news.getMediaType(),
                news.getSourcePublishedAt().toString(),
                newsImageService.resolveCoverImage(news.getId(), news.getCoverImage()),
                news.getTags() != null ? Arrays.asList(news.getTags().split(",")) : List.of(),
                news.getHotScore(),
                news.getAuthor(),
                news.getUrl()
        );
    }

    private NewsArticle convertToArticle(News news) {
        List<String> contentImages = extractContentImages(news.getContent(), news.getCoverImage());
        return new NewsArticle(
                news.getId(),
                news.getTitle(),
                news.getSummary(),
                news.getSource(),
                news.getSourceType(),
                news.getMediaType(),
                news.getSourcePublishedAt().toString(),
                news.getAuthor(),
                newsImageService.resolveCoverImage(news.getId(), news.getCoverImage()),
                news.getTags() != null ? Arrays.asList(news.getTags().split(",")) : List.of(),
                news.getRelatedTeamIds() != null
                        ? Arrays.stream(news.getRelatedTeamIds().split(","))
                        .map(Long::parseLong)
                        .collect(Collectors.toList()) : List.of(),
                news.getRelatedPlayerIds() != null
                        ? Arrays.stream(news.getRelatedPlayerIds().split(","))
                        .map(Long::parseLong)
                        .collect(Collectors.toList()) : List.of(),
                news.getHotScore(),
                news.getUrl(),
                news.getSourceNote(),
                newsImageService.mergeDetailImages(news.getCoverImage(), contentImages),
                parseTextBlocks(news.getContent())
        );
    }

    private TransferNews convertToTransferNews(News news) {
        return new TransferNews(
                news.getId(),
                news.getTitle(),
                news.getSummary(),
                news.getSource(),
                news.getSourceType(),
                news.getSourcePublishedAt().toString(),
                news.getRelatedTeamIds() != null
                        ? Arrays.stream(news.getRelatedTeamIds().split(","))
                        .map(Long::parseLong)
                        .collect(Collectors.toList()) : List.of(),
                news.getRelatedPlayerIds() != null
                        ? Arrays.stream(news.getRelatedPlayerIds().split(","))
                        .map(Long::parseLong)
                        .collect(Collectors.toList()) : List.of(),
                news.getHotScore(),
                news.getUrl()
        );
    }

    private List<ArticleBlock> parseTextBlocks(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(content.split("\n\n"))
                .filter(p -> !p.trim().isEmpty())
                .filter(p -> !isImageBlock(p))
                .map(ArticleBlock::paragraph)
                .collect(Collectors.toList());
    }

    private List<String> extractContentImages(String content, String coverImage) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }

        String coverKey = imageIdentity(coverImage);
        Set<String> seen = new LinkedHashSet<>();
        List<String> images = new ArrayList<>();
        for (String block : content.split("\n\n")) {
            String trimmed = block.trim();
            if (!isImageBlock(trimmed)) {
                continue;
            }

            String imageUrl = trimmed.substring(5, trimmed.length() - 1);
            String imageKey = imageIdentity(imageUrl);
            if (imageKey == null || imageKey.equals(coverKey) || !seen.add(imageKey)) {
                continue;
            }
            images.add(imageUrl);
        }
        return images;
    }

    private boolean isImageBlock(String block) {
        return block.startsWith("[IMG:") && block.endsWith("]");
    }

    private String imageIdentity(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        String clean = imageUrl;
        int queryIndex = clean.indexOf('?');
        if (queryIndex >= 0) {
            clean = clean.substring(0, queryIndex);
        }
        int slashIndex = clean.lastIndexOf('/');
        return slashIndex >= 0 ? clean.substring(slashIndex + 1) : clean;
    }

    private boolean isAllowedNews(News news) {
        if (news == null) {
            return false;
        }

        if (news.getSourceType() != null && BLOCKED_SOURCE_TYPES.contains(news.getSourceType().toLowerCase(Locale.ROOT))) {
            return false;
        }

        return !containsBlockedKeyword(news.getTitle())
                && !containsBlockedKeyword(news.getSummary())
                && !containsBlockedKeyword(news.getTags())
                && !containsBlockedKeyword(news.getSourceNote())
                && !containsBlockedKeyword(news.getContent());
    }

    private boolean containsBlockedKeyword(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String normalized = value.toLowerCase(Locale.ROOT);
        return BLOCKED_NEWS_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    private Specification<News> visibleNewsSpec(String sourceType, String tag, String keyword, boolean preferFeaturedSources) {
        String normalizedSourceType = normalizeParam(sourceType);
        String normalizedTag = normalizeParam(tag);
        String normalizedKeyword = normalizeParam(keyword);

        return (root, query, cb) -> {
            applyNewsOrdering(root, query, cb, preferFeaturedSources);

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.not(root.get("sourceType").in(BLOCKED_SOURCE_TYPES)));

            for (String blockedKeyword : BLOCKED_NEWS_KEYWORDS) {
                String pattern = "%" + blockedKeyword.toLowerCase(Locale.ROOT) + "%";
                predicates.add(notLikeIgnoreCase(cb, root.get("title"), pattern));
                predicates.add(notLikeIgnoreCase(cb, root.get("summary"), pattern));
                predicates.add(notLikeIgnoreCase(cb, root.get("tags"), pattern));
                predicates.add(notLikeIgnoreCase(cb, root.get("sourceNote"), pattern));
                predicates.add(notLikeIgnoreCase(cb, root.get("content"), pattern));
            }

            if (normalizedSourceType != null) {
                predicates.add(cb.equal(cb.lower(root.get("sourceType")), normalizedSourceType.toLowerCase(Locale.ROOT)));
            }

            if (normalizedTag != null) {
                predicates.add(cb.like(cb.lower(root.get("tags")), "%" + normalizedTag.toLowerCase(Locale.ROOT) + "%"));
            }

            if (normalizedKeyword != null) {
                String likePattern = "%" + normalizedKeyword.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), likePattern),
                        cb.like(cb.lower(root.get("summary")), likePattern)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private boolean shouldBlendFeaturedSources(int page, String sourceType, String tag, String keyword) {
        return page <= FEATURED_FEED_MAX_PAGE
                && normalizeParam(sourceType) == null
                && normalizeParam(tag) == null
                && normalizeParam(keyword) == null;
    }

    private PageResult<NewsListItem> getBlendedNewsList(int page, int pageSize, String tag, String keyword) {
        int targetCount = page * pageSize;
        int fetchSize = Math.max(FEATURED_FEED_MIN_WINDOW, targetCount * FEATURED_FEED_WINDOW_MULTIPLIER);
        int featuredFetchSize = Math.max(FEATURED_FEED_SOURCE_BATCH_SIZE, targetCount * 2);

        Page<News> allCandidatePage = newsRepository.findAll(
                visibleNewsSpec(null, tag, keyword, false),
                PageRequest.of(0, fetchSize)
        );
        List<News> officialNews = newsRepository.findAll(
                visibleNewsSpec(SOURCE_OFFICIAL, tag, keyword, false),
                PageRequest.of(0, featuredFetchSize)
        ).getContent();
        List<News> skyNews = newsRepository.findAll(
                visibleNewsSpec(SOURCE_SKY, tag, keyword, false),
                PageRequest.of(0, featuredFetchSize)
        ).getContent();

        List<News> blendedNews = blendFeaturedSources(allCandidatePage.getContent(), officialNews, skyNews);

        int fromIndex = Math.min((page - 1) * pageSize, blendedNews.size());
        int toIndex = Math.min(fromIndex + pageSize, blendedNews.size());

        List<NewsListItem> items = blendedNews.subList(fromIndex, toIndex).stream()
                .map(this::convertToListItem)
                .collect(Collectors.toList());

        log.debug("[NewsService] Returning {} blended items from DB", items.size());
        return PageResult.of(items, page, pageSize, allCandidatePage.getTotalElements());
    }

    private List<News> blendFeaturedSources(List<News> allCandidates, List<News> officialCandidates, List<News> skyCandidates) {
        Map<String, News> uniqueOfficialNews = new LinkedHashMap<>();
        for (News candidate : officialCandidates) {
            uniqueOfficialNews.putIfAbsent(candidate.getId(), candidate);
        }

        Map<String, News> uniqueSkyNews = new LinkedHashMap<>();
        for (News candidate : skyCandidates) {
            uniqueSkyNews.putIfAbsent(candidate.getId(), candidate);
        }

        List<News> otherNews = new ArrayList<>();

        for (News candidate : allCandidates) {
            String normalizedSourceType = normalizeParam(candidate.getSourceType());
            if (!SOURCE_OFFICIAL.equalsIgnoreCase(normalizedSourceType) && !SOURCE_SKY.equalsIgnoreCase(normalizedSourceType)) {
                otherNews.add(candidate);
            }
        }

        List<News> officialNews = new ArrayList<>(uniqueOfficialNews.values());
        List<News> skyNews = new ArrayList<>(uniqueSkyNews.values());

        List<News> blended = new ArrayList<>(allCandidates.size() + officialNews.size() + skyNews.size());
        int officialIndex = 0;
        int skyIndex = 0;
        int otherIndex = 0;

        while (officialIndex < officialNews.size() || skyIndex < skyNews.size() || otherIndex < otherNews.size()) {
            boolean added = false;

            if (officialIndex < officialNews.size()) {
                blended.add(officialNews.get(officialIndex++));
                added = true;
            }
            if (skyIndex < skyNews.size()) {
                blended.add(skyNews.get(skyIndex++));
                added = true;
            }
            for (int i = 0; i < FEATURED_FEED_OTHER_BATCH_SIZE && otherIndex < otherNews.size(); i++) {
                blended.add(otherNews.get(otherIndex++));
                added = true;
            }

            if (!added) {
                break;
            }
        }

        return blended;
    }

    private void applyNewsOrdering(Root<News> root, CriteriaQuery<?> query, CriteriaBuilder cb, boolean preferFeaturedSources) {
        Class<?> resultType = query.getResultType();
        if (Long.class.equals(resultType) || long.class.equals(resultType)) {
            return;
        }

        Path<String> sourceTypePath = root.get("sourceType");
        Path<Integer> hotScorePath = root.get("hotScore");
        Path<LocalDateTime> publishedAtPath = root.get("sourcePublishedAt");
        LocalDateTime now = LocalDateTime.now();

        Expression<Integer> freshnessRank = cb.<Integer>selectCase()
                .when(cb.greaterThanOrEqualTo(publishedAtPath, now.minusHours(12)), 3)
                .when(cb.greaterThanOrEqualTo(publishedAtPath, now.minusDays(1)), 2)
                .when(cb.greaterThanOrEqualTo(publishedAtPath, now.minusDays(3)), 1)
                .otherwise(0);

        Expression<Integer> sourcePriority = cb.<Integer>selectCase()
                .when(cb.equal(sourceTypePath, SOURCE_OFFICIAL), 10)
                .when(cb.equal(sourceTypePath, SOURCE_SKY), 9)
                .when(cb.equal(sourceTypePath, SOURCE_GUARDIAN), 3)
                .when(cb.equal(sourceTypePath, SOURCE_ROMANO), 2)
                .when(cb.equal(sourceTypePath, SOURCE_REDDIT), 1)
                .otherwise(0);

        if (preferFeaturedSources) {
            query.orderBy(
                    cb.desc(sourcePriority),
                    cb.desc(freshnessRank),
                    cb.desc(cb.coalesce(hotScorePath, 0)),
                    cb.desc(publishedAtPath)
            );
            return;
        }

        query.orderBy(
                cb.desc(freshnessRank),
                cb.desc(cb.coalesce(hotScorePath, 0)),
                cb.desc(publishedAtPath)
        );
    }

    private Predicate notLikeIgnoreCase(CriteriaBuilder cb, Path<String> path, String pattern) {
        return cb.or(cb.isNull(path), cb.notLike(cb.lower(path), pattern));
    }

    private String normalizeParam(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
