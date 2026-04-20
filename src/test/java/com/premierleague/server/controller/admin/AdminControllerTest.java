package com.premierleague.server.controller.admin;

import com.premierleague.server.dto.ApiResponse;
import com.premierleague.server.provider.DongqiudiProvider;
import com.premierleague.server.provider.NewsProvider;
import com.premierleague.server.repository.FetchLogRepository;
import com.premierleague.server.repository.NewsRepository;
import com.premierleague.server.service.NewsFetchService;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AdminControllerTest {

    @Test
    void sourceStatusAndHealthExcludeDisabledProviders() {
        NewsProvider enabledProvider = provider("romano", "Romano", true);
        NewsProvider disabledProvider = provider("x", "X", false);

        AdminController controller = new AdminController(
                mock(NewsFetchService.class),
                mock(FetchLogRepository.class),
                mock(NewsRepository.class),
                mock(DongqiudiProvider.class),
                List.of(enabledProvider, disabledProvider),
                new ConcurrentMapCacheManager("newsList", "newsDetail", "transferNews", "socialPlayers")
        );

        ApiResponse<List<Map<String, Object>>> sourceStatus = controller.getSourceStatus();
        ApiResponse<Map<String, Object>> health = controller.healthCheck();

        assertEquals(1, sourceStatus.data().size());
        assertEquals("romano", sourceStatus.data().get(0).get("sourceType"));
        assertEquals(1, health.data().get("sources"));
        assertEquals(1L, health.data().get("sourcesAvailable"));
    }

    private NewsProvider provider(String sourceType, String sourceName, boolean enabled) {
        return new NewsProvider() {
            @Override
            public String getSourceType() {
                return sourceType;
            }

            @Override
            public String getSourceName() {
                return sourceName;
            }

            @Override
            public List<com.premierleague.server.entity.News> fetchLatest(int maxItems) {
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
        };
    }
}
