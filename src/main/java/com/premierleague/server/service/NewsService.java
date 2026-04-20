package com.premierleague.server.service;

import com.premierleague.server.dto.PageResult;
import com.premierleague.server.entity.News;
import com.premierleague.server.model.ArticleBlock;
import com.premierleague.server.model.NewsArticle;
import com.premierleague.server.model.NewsListItem;
import com.premierleague.server.model.TransferNews;
import com.premierleague.server.repository.NewsRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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

        Pageable pageable = PageRequest.of(
                safePage - 1,
                safePageSize,
                Sort.by(Sort.Direction.DESC, "sourcePublishedAt")
        );
        Page<News> newsPage = newsRepository.findAll(visibleNewsSpec(sourceType, tag, keyword), pageable);

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

    private Specification<News> visibleNewsSpec(String sourceType, String tag, String keyword) {
        String normalizedSourceType = normalizeParam(sourceType);
        String normalizedTag = normalizeParam(tag);
        String normalizedKeyword = normalizeParam(keyword);

        return (root, query, cb) -> {
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
