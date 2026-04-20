package com.premierleague.server.service;

import com.premierleague.server.config.props.FetchSourceProperties;
import com.premierleague.server.entity.FetchLog;
import com.premierleague.server.entity.News;
import com.premierleague.server.provider.NewsProvider;
import com.premierleague.server.repository.FetchLogRepository;
import com.premierleague.server.repository.NewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsFetchService {

    private final List<NewsProvider> providers;
    private final NewsRepository newsRepository;
    private final FetchLogRepository fetchLogRepository;
    private final DuplicateCheckService duplicateCheckService;
    private final ContentCleanService contentCleanService;
    private final NewsContentEnrichmentService newsContentEnrichmentService;
    private final NewsOriginalContentService newsOriginalContentService;
    private final NewsTranslationService newsTranslationService;
    private final FetchSourceProperties fetchProps;

    @Transactional
    public void fetchByFrequency(String frequencyLevel) {
        List<String> sources = getSourcesByFrequency(frequencyLevel);

        log.info("[Fetch] Starting {} frequency fetch for sources: {}", frequencyLevel, sources);
        for (String sourceType : sources) {
            providers.stream()
                    .filter(NewsProvider::isEnabled)
                    .filter(provider -> provider.getSourceType().equals(sourceType))
                    .findFirst()
                    .ifPresent(provider -> fetchFromProvider(provider, frequencyLevel));
        }
    }

    @Transactional
    public void fetchFromProvider(NewsProvider provider, String frequencyLevel) {
        String sourceType = provider.getSourceType();
        String batchId = generateBatchId(sourceType);
        int maxItems = getMaxItemsByFrequency(frequencyLevel);

        FetchLog logEntry = FetchLog.builder()
                .batchId(batchId)
                .sourceType(sourceType)
                .frequencyLevel(frequencyLevel)
                .startedAt(LocalDateTime.now())
                .status("running")
                .build();
        fetchLogRepository.save(logEntry);

        int newCount = 0;
        int duplicateCount = 0;
        int failedCount = 0;
        int updatedCount = 0;

        try {
            List<News> fetchedNews = provider.fetchLatest(maxItems);
            logEntry.setRequestCount(fetchedNews.size());
            log.info("[Fetch] {} fetched {} items", sourceType, fetchedNews.size());

            for (News news : fetchedNews) {
                try {
                    String fingerprint = duplicateCheckService.generateFingerprint(news);

                    news = newsContentEnrichmentService.enrich(news);
                    news = newsOriginalContentService.ensureContent(news);
                    news = contentCleanService.cleanNews(news);
                    news = newsTranslationService.translateForStorage(news);
                    news = contentCleanService.cleanNews(news);

                    if (news.getTags() == null || news.getTags().isEmpty()) {
                        String extractedTags = contentCleanService.extractTags(news.getTitle(), news.getContent());
                        if (!extractedTags.isEmpty()) {
                            news.setTags(extractedTags);
                        }
                    }

                    news.setFetchedAt(LocalDateTime.now());
                    news.setFetchBatchId(batchId);

                    boolean duplicate = newsRepository.existsByFingerprint(fingerprint);
                    if (duplicate) {
                        News existing = newsRepository.findByFingerprint(fingerprint).orElse(null);
                        if (existing != null && mergeNews(existing, news, batchId)) {
                            updatedCount++;
                            log.debug("[Fetch] Updated existing news: {}", fingerprint);
                        } else {
                            duplicateCount++;
                        }
                        continue;
                    }

                    news.setFingerprint(fingerprint);
                    news.setId(fingerprint);
                    newsRepository.save(news);
                    newCount++;
                } catch (Exception e) {
                    failedCount++;
                    log.error("[Fetch] Failed to process news: {}", news.getTitle(), e);
                }
            }

            logEntry.setNewCount(newCount);
            logEntry.setUpdatedCount(updatedCount);
            logEntry.setDuplicateCount(duplicateCount);
            logEntry.setFailedCount(failedCount);
            logEntry.complete();

            log.info("[Fetch] Completed {}: new={}, updated={}, duplicate={}, failed={}",
                    sourceType, newCount, updatedCount, duplicateCount, failedCount);
        } catch (Exception e) {
            log.error("[Fetch] Failed to fetch from {}", sourceType, e);
            logEntry.fail(e.getMessage());
        }

        fetchLogRepository.save(logEntry);
    }

    @Transactional
    public void fetchAll() {
        log.info("[Fetch] Starting full fetch for all providers");
        for (NewsProvider provider : providers.stream().filter(NewsProvider::isEnabled).toList()) {
            fetchFromProvider(provider, provider.getFrequencyLevel());
        }
    }

    @Transactional
    public Map<String, Object> backfillNews(String sourceType, int limit) {
        List<String> sourceTypes = resolveBackfillSources(sourceType);
        int updated = 0;
        int skipped = 0;
        int failed = 0;

        for (String currentSourceType : sourceTypes) {
            List<News> records = newsRepository
                    .findBySourceTypeOrderBySourcePublishedAtDesc(currentSourceType, PageRequest.of(0, limit))
                    .getContent();

            for (News existing : records) {
                try {
                    if (!newsContentEnrichmentService.needsEnrichment(existing)
                            && !newsOriginalContentService.needsOriginalContent(existing)
                            && !newsTranslationService.needsTranslation(existing)
                            && !newsTranslationService.needsCleanup(existing)) {
                        skipped++;
                        continue;
                    }

                    News workingCopy = cloneNews(existing);
                    workingCopy = newsContentEnrichmentService.enrich(workingCopy);
                    workingCopy = newsOriginalContentService.ensureContent(workingCopy);
                    workingCopy = contentCleanService.cleanNews(workingCopy);
                    workingCopy = newsTranslationService.translateForStorage(workingCopy);
                    workingCopy = contentCleanService.cleanNews(workingCopy);

                    if (mergeNews(existing, workingCopy, existing.getFetchBatchId())) {
                        updated++;
                    } else {
                        skipped++;
                    }
                } catch (Exception e) {
                    failed++;
                    log.warn("[Backfill] Failed to backfill {} / {}", currentSourceType, existing.getId(), e);
                }
            }
        }

        return Map.of(
                "sources", sourceTypes,
                "limitPerSource", limit,
                "updated", updated,
                "skipped", skipped,
                "failed", failed
        );
    }

    @Transactional
    public Map<String, Object> backfillNewsById(String id) {
        News existing = newsRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("News not found: " + id));

        Map<String, Object> before = inspectState(existing);

        News workingCopy = cloneNews(existing);
        workingCopy = newsContentEnrichmentService.enrich(workingCopy);
        workingCopy = newsOriginalContentService.ensureContent(workingCopy);
        workingCopy = contentCleanService.cleanNews(workingCopy);
        workingCopy = newsTranslationService.translateForStorage(workingCopy);
        workingCopy = contentCleanService.cleanNews(workingCopy);

        boolean updated = mergeNews(existing, workingCopy, existing.getFetchBatchId());
        News latest = updated
                ? newsRepository.findById(id).orElse(existing)
                : existing;

        return Map.of(
                "id", id,
                "updated", updated,
                "before", before,
                "after", inspectState(latest)
        );
    }

    public Map<String, Object> getFetchStats(String sourceType, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        List<Object[]> stats = fetchLogRepository.getStatsBySourceType(sourceType, since);

        if (stats.isEmpty() || stats.get(0)[0] == null) {
            return Map.of(
                    "fetchCount", 0,
                    "totalNew", 0,
                    "totalDuplicate", 0
            );
        }

        Object[] row = stats.get(0);
        return Map.of(
                "fetchCount", ((Number) row[0]).intValue(),
                "totalNew", row[1] != null ? ((Number) row[1]).intValue() : 0,
                "totalDuplicate", row[2] != null ? ((Number) row[2]).intValue() : 0
        );
    }

    public Map<String, Object> inspectBackfillState(String id) {
        News news = newsRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("News not found: " + id));

        return inspectState(news);
    }

    private Map<String, Object> inspectState(News news) {
        String summary = news.getSummary() == null ? "" : news.getSummary();
        String content = news.getContent() == null ? "" : news.getContent();
        return Map.of(
                "id", news.getId(),
                "sourceType", news.getSourceType(),
                "title", news.getTitle(),
                "summaryLength", summary.length(),
                "contentLength", content.length(),
                "summaryEqualsContent", summary.equals(content),
                "needsEnrichment", newsContentEnrichmentService.needsEnrichment(news),
                "needsOriginalContent", newsOriginalContentService.needsOriginalContent(news),
                "needsTranslation", newsTranslationService.needsTranslation(news),
                "needsCleanup", newsTranslationService.needsCleanup(news)
        );
    }

    private boolean mergeNews(News existing, News fetched, String batchId) {
        boolean changed = shouldUpdate(existing, fetched);

        changed |= updateTextField(existing::getTitle, existing::setTitle, fetched.getTitle());
        changed |= updateTextField(existing::getSummary, existing::setSummary, fetched.getSummary());
        changed |= updateTextField(existing::getContent, existing::setContent, fetched.getContent());
        changed |= updateTextField(existing::getAuthor, existing::setAuthor, fetched.getAuthor());
        changed |= updateTextField(existing::getCoverImage, existing::setCoverImage, fetched.getCoverImage());
        changed |= updateTextField(existing::getTags, existing::setTags, fetched.getTags());
        changed |= updateTextField(existing::getSourceNote, existing::setSourceNote, fetched.getSourceNote());

        if (fetched.getHotScore() != null && !fetched.getHotScore().equals(existing.getHotScore())) {
            existing.setHotScore(fetched.getHotScore());
            changed = true;
        }
        if (fetched.getMediaType() != null && !fetched.getMediaType().equals(existing.getMediaType())) {
            existing.setMediaType(fetched.getMediaType());
            changed = true;
        }

        if (changed) {
            existing.setContentUpdatedAt(LocalDateTime.now());
            existing.setFetchBatchId(batchId);
            newsRepository.save(existing);
        }

        return changed;
    }

    private boolean updateTextField(java.util.function.Supplier<String> getter,
                                    java.util.function.Consumer<String> setter,
                                    String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.equals(getter.get())) {
            return false;
        }
        setter.accept(candidate);
        return true;
    }

    private boolean shouldUpdate(News existing, News fetched) {
        boolean existingHasContent = existing.getContent() != null && !existing.getContent().isEmpty();
        boolean fetchedHasContent = fetched.getContent() != null && !fetched.getContent().isEmpty();
        if (!existingHasContent && fetchedHasContent) {
            return true;
        }

        if (existingHasContent && fetchedHasContent
                && fetched.getContent().length() > existing.getContent().length() * 1.2) {
            return true;
        }

        if (existing.getHotScore() != null && fetched.getHotScore() != null
                && Math.abs(fetched.getHotScore() - existing.getHotScore()) > 10) {
            return true;
        }

        return false;
    }

    private List<String> resolveBackfillSources(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return List.of("sky", "official", "guardian", "romano", "reddit", "x");
        }
        return List.of(sourceType);
    }

    private News cloneNews(News source) {
        return News.builder()
                .id(source.getId())
                .title(source.getTitle())
                .summary(source.getSummary())
                .source(source.getSource())
                .sourceType(source.getSourceType())
                .mediaType(source.getMediaType())
                .sourcePublishedAt(source.getSourcePublishedAt())
                .author(source.getAuthor())
                .coverImage(source.getCoverImage())
                .tags(source.getTags())
                .relatedTeamIds(source.getRelatedTeamIds())
                .relatedPlayerIds(source.getRelatedPlayerIds())
                .hotScore(source.getHotScore())
                .url(source.getUrl())
                .sourceNote(source.getSourceNote())
                .content(source.getContent())
                .fingerprint(source.getFingerprint())
                .fetchedAt(source.getFetchedAt())
                .contentUpdatedAt(source.getContentUpdatedAt())
                .fetchBatchId(source.getFetchBatchId())
                .fetchStatus(source.getFetchStatus())
                .fetchError(source.getFetchError())
                .build();
    }

    private List<String> getSourcesByFrequency(String frequencyLevel) {
        return switch (frequencyLevel.toLowerCase()) {
            case "high" -> fetchProps.getHighFrequency().getSources();
            case "medium" -> fetchProps.getMediumFrequency().getSources();
            case "low" -> fetchProps.getLowFrequency().getSources();
            default -> List.of();
        };
    }

    private int getMaxItemsByFrequency(String frequencyLevel) {
        return switch (frequencyLevel.toLowerCase()) {
            case "high" -> fetchProps.getHighFrequency().getMaxItemsPerFetch();
            case "medium" -> fetchProps.getMediumFrequency().getMaxItemsPerFetch();
            case "low" -> fetchProps.getLowFrequency().getMaxItemsPerFetch();
            default -> 50;
        };
    }

    private String generateBatchId(String sourceType) {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "_" + sourceType;
    }
}
