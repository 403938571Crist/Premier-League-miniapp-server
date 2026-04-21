package com.premierleague.server.service;

import com.premierleague.server.dto.PageResult;
import com.premierleague.server.entity.News;
import com.premierleague.server.model.NewsListItem;
import com.premierleague.server.repository.NewsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NewsServiceTest {

    private NewsRepository newsRepository;
    private NewsImageService newsImageService;
    private NewsService newsService;

    @BeforeEach
    void setUp() {
        newsRepository = mock(NewsRepository.class);
        newsImageService = mock(NewsImageService.class);
        newsService = new NewsService(newsRepository, newsImageService);

        when(newsImageService.resolveCoverImage(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
    }

    @Test
    void blendsOfficialSkyAndRomanoIntoGeneralFeed() {
        LocalDateTime now = LocalDateTime.now();
        List<News> allCandidates = List.of(
                news("z1", "Zhibo 1", "zhibo8", now.minusHours(2)),
                news("d1", "Dongqiudi 1", "dongqiudi", now.minusHours(3)),
                news("o1", "Official 1", "official", now.minusHours(4)),
                news("r1", "Romano 1", "romano", now.minusHours(5)),
                news("z2", "Zhibo 2", "zhibo8", now.minusHours(6)),
                news("s1", "Sky 1", "sky", now.minusDays(1)),
                news("d2", "Dongqiudi 2", "dongqiudi", now.minusHours(8)),
                news("o2", "Official 2", "official", now.minusDays(1).minusHours(1)),
                news("s2", "Sky 2", "sky", now.minusDays(2))
        );
        List<News> officialCandidates = List.of(
                news("o1", "Official 1", "official", now.minusHours(4)),
                news("o2", "Official 2", "official", now.minusDays(1).minusHours(1))
        );
        List<News> skyCandidates = List.of(
                news("s1", "Sky 1", "sky", now.minusDays(1)),
                news("s2", "Sky 2", "sky", now.minusDays(2))
        );
        List<News> romanoCandidates = List.of(
                news("r1", "Romano 1", "romano", now.minusHours(5))
        );

        when(newsRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(
                        new PageImpl<>(allCandidates, PageRequest.of(0, 120), allCandidates.size()),
                        new PageImpl<>(officialCandidates, PageRequest.of(0, 24), officialCandidates.size()),
                        new PageImpl<>(skyCandidates, PageRequest.of(0, 24), skyCandidates.size()),
                        new PageImpl<>(romanoCandidates, PageRequest.of(0, 24), romanoCandidates.size())
                );

        PageResult<NewsListItem> result = newsService.getNewsList(1, 6, null, null, null);

        assertEquals(List.of("official", "sky", "romano", "zhibo8", "dongqiudi", "zhibo8"),
                result.list().stream().map(NewsListItem::sourceType).toList());
        assertEquals(List.of("Official 1", "Sky 1", "Romano 1", "Zhibo 1", "Dongqiudi 1", "Zhibo 2"),
                result.list().stream().map(NewsListItem::title).toList());
    }

    @Test
    void doesNotBlendStaleRomanoIntoGeneralFeed() {
        LocalDateTime now = LocalDateTime.now();
        List<News> allCandidates = List.of(
                news("z1", "Zhibo 1", "zhibo8", now.minusHours(2)),
                news("d1", "Dongqiudi 1", "dongqiudi", now.minusHours(3)),
                news("o1", "Official 1", "official", now.minusHours(4)),
                news("r1", "Romano 1", "romano", now.minusDays(8)),
                news("z2", "Zhibo 2", "zhibo8", now.minusHours(6)),
                news("s1", "Sky 1", "sky", now.minusDays(1))
        );
        List<News> officialCandidates = List.of(
                news("o1", "Official 1", "official", now.minusHours(4))
        );
        List<News> skyCandidates = List.of(
                news("s1", "Sky 1", "sky", now.minusDays(1))
        );
        List<News> romanoCandidates = List.of(
                news("r1", "Romano 1", "romano", now.minusDays(8))
        );

        when(newsRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(
                        new PageImpl<>(allCandidates, PageRequest.of(0, 120), allCandidates.size()),
                        new PageImpl<>(officialCandidates, PageRequest.of(0, 24), officialCandidates.size()),
                        new PageImpl<>(skyCandidates, PageRequest.of(0, 24), skyCandidates.size()),
                        new PageImpl<>(romanoCandidates, PageRequest.of(0, 24), romanoCandidates.size())
                );

        PageResult<NewsListItem> result = newsService.getNewsList(1, 5, null, null, null);

        assertEquals(List.of("official", "sky", "zhibo8", "dongqiudi", "zhibo8"),
                result.list().stream().map(NewsListItem::sourceType).toList());
    }

    private News news(String id, String title, String sourceType, LocalDateTime publishedAt) {
        return News.builder()
                .id(id)
                .title(title)
                .summary(title + " summary")
                .source(sourceType)
                .sourceType(sourceType)
                .mediaType("article")
                .sourcePublishedAt(publishedAt)
                .hotScore(60)
                .build();
    }
}
