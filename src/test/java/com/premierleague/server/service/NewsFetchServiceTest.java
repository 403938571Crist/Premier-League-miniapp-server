package com.premierleague.server.service;

import com.premierleague.server.config.props.FetchSourceProperties;
import com.premierleague.server.entity.FetchLog;
import com.premierleague.server.entity.News;
import com.premierleague.server.provider.NewsProvider;
import com.premierleague.server.repository.FetchLogRepository;
import com.premierleague.server.repository.NewsRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NewsFetchServiceTest {

    @Test
    void fetchAllSkipsDisabledProviders() {
        CountingProvider enabledProvider = new CountingProvider("romano", true);
        CountingProvider disabledProvider = new CountingProvider("x", false);

        FetchLogRepository fetchLogRepository = mock(FetchLogRepository.class);
        when(fetchLogRepository.save(any(FetchLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NewsFetchService newsFetchService = new NewsFetchService(
                List.of(enabledProvider, disabledProvider),
                mock(NewsRepository.class),
                fetchLogRepository,
                mock(DuplicateCheckService.class),
                mock(ContentCleanService.class),
                mock(NewsContentEnrichmentService.class),
                mock(NewsOriginalContentService.class),
                mock(NewsTranslationService.class),
                new FetchSourceProperties()
        );

        newsFetchService.fetchAll();

        assertEquals(1, enabledProvider.fetchCount);
        assertEquals(0, disabledProvider.fetchCount);
    }

    private static final class CountingProvider implements NewsProvider {
        private final String sourceType;
        private final boolean enabled;
        private int fetchCount;

        private CountingProvider(String sourceType, boolean enabled) {
            this.sourceType = sourceType;
            this.enabled = enabled;
        }

        @Override
        public String getSourceType() {
            return sourceType;
        }

        @Override
        public String getSourceName() {
            return sourceType;
        }

        @Override
        public List<News> fetchLatest(int maxItems) {
            fetchCount++;
            return List.of();
        }

        @Override
        public boolean isAvailable() {
            return enabled;
        }

        @Override
        public String getFrequencyLevel() {
            return "high";
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }
    }
}
