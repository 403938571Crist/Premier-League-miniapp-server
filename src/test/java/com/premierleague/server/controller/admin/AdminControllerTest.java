package com.premierleague.server.controller.admin;

import com.premierleague.server.dto.ApiResponse;
import com.premierleague.server.provider.DongqiudiProvider;
import com.premierleague.server.provider.NewsProvider;
import com.premierleague.server.repository.FetchLogRepository;
import com.premierleague.server.repository.NewsRepository;
import com.premierleague.server.service.NewsFetchService;
import com.premierleague.server.service.PlayerProfileBackfillService;
import com.premierleague.server.service.PlayerSocialBackfillService;
import com.premierleague.server.service.PlayerSquadBackfillService;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminControllerTest {

    @Test
    void sourceStatusExcludesDisabledProviders() {
        NewsProvider enabledProvider = provider("romano", "Romano", true);
        NewsProvider disabledProvider = provider("x", "X", false);

        AdminController controller = new AdminController(
                mock(NewsFetchService.class),
                mock(PlayerProfileBackfillService.class),
                mock(PlayerSquadBackfillService.class),
                mock(PlayerSocialBackfillService.class),
                mock(FetchLogRepository.class),
                mock(NewsRepository.class),
                mock(DongqiudiProvider.class),
                List.of(enabledProvider, disabledProvider),
                new ConcurrentMapCacheManager("newsList", "newsDetail", "transferNews", "socialPlayers")
        );

        // /api/admin/health 已移除（与 /actuator/health 重叠），只验证 source-status
        ApiResponse<List<Map<String, Object>>> sourceStatus = controller.getSourceStatus();

        assertEquals(1, sourceStatus.data().size());
        assertEquals("romano", sourceStatus.data().get(0).get("sourceType"));
    }

    @Test
    void backfillBig6PlayersReturnsScopedPayload() {
        PlayerProfileBackfillService playerProfileBackfillService = mock(PlayerProfileBackfillService.class);
        when(playerProfileBackfillService.backfillMissingProfiles(150, List.of(1L, 2L, 3L, 5L, 6L, 18L)))
                .thenReturn(new PlayerProfileBackfillService.BackfillResult(28, 20, 12, 8));

        AdminController controller = new AdminController(
                mock(NewsFetchService.class),
                playerProfileBackfillService,
                mock(PlayerSquadBackfillService.class),
                mock(PlayerSocialBackfillService.class),
                mock(FetchLogRepository.class),
                mock(NewsRepository.class),
                mock(DongqiudiProvider.class),
                List.of(),
                new ConcurrentMapCacheManager("teamSquad", "playerDetail", "playerByApiId")
        );

        ApiResponse<Map<String, Object>> response = controller.backfillBig6Players(150);

        assertEquals("big6", response.data().get("scope"));
        assertEquals(List.of(1L, 2L, 3L, 5L, 6L, 18L), response.data().get("teamIds"));
        assertEquals(28, response.data().get("scanned"));
        assertEquals(20, response.data().get("updated"));
        assertEquals(12, response.data().get("chineseNamesUpdated"));
        assertEquals(8, response.data().get("photosUpdated"));
    }

    @Test
    void backfillPlayersAcceptsOptionalTeamScope() {
        PlayerProfileBackfillService playerProfileBackfillService = mock(PlayerProfileBackfillService.class);
        List<Long> teamIds = List.of(12L, 13L, 14L);
        when(playerProfileBackfillService.backfillMissingProfiles(60, teamIds))
                .thenReturn(new PlayerProfileBackfillService.BackfillResult(46, 9, 0, 9));

        AdminController controller = new AdminController(
                mock(NewsFetchService.class),
                playerProfileBackfillService,
                mock(PlayerSquadBackfillService.class),
                mock(PlayerSocialBackfillService.class),
                mock(FetchLogRepository.class),
                mock(NewsRepository.class),
                mock(DongqiudiProvider.class),
                List.of(),
                new ConcurrentMapCacheManager("teamSquad", "playerDetail", "playerByApiId")
        );

        ApiResponse<Map<String, Object>> response = controller.backfillPlayers(60, teamIds);

        assertEquals(teamIds, response.data().get("teamIds"));
        assertEquals(46, response.data().get("scanned"));
        assertEquals(9, response.data().get("updated"));
        assertEquals(0, response.data().get("chineseNamesUpdated"));
        assertEquals(9, response.data().get("photosUpdated"));
    }

    @Test
    void backfillPlayerSquadsReturnsServiceCounters() {
        PlayerSquadBackfillService playerSquadBackfillService = mock(PlayerSquadBackfillService.class);
        when(playerSquadBackfillService.backfillOfficialSquads(eq(List.of(5L, 6L))))
                .thenReturn(new PlayerSquadBackfillService.BackfillResult(2, 53, 4, 11, 9));

        AdminController controller = new AdminController(
                mock(NewsFetchService.class),
                mock(PlayerProfileBackfillService.class),
                playerSquadBackfillService,
                mock(PlayerSocialBackfillService.class),
                mock(FetchLogRepository.class),
                mock(NewsRepository.class),
                mock(DongqiudiProvider.class),
                List.of(),
                new ConcurrentMapCacheManager("teamSquad", "playerDetail", "playerByApiId")
        );

        ApiResponse<Map<String, Object>> response = controller.backfillPlayerSquads(List.of(5L, 6L));

        assertEquals(List.of(5L, 6L), response.data().get("teamIds"));
        assertEquals(2, response.data().get("scannedTeams"));
        assertEquals(53, response.data().get("scannedPlayers"));
        assertEquals(4, response.data().get("createdPlayers"));
        assertEquals(11, response.data().get("updatedPlayers"));
        assertEquals(9, response.data().get("photosUpdated"));
    }

    @Test
    void backfillPlayerSocialReturnsServiceCounters() {
        PlayerSocialBackfillService playerSocialBackfillService = mock(PlayerSocialBackfillService.class);
        when(playerSocialBackfillService.backfillPlayerSocials(50, List.of(5L)))
                .thenReturn(new PlayerSocialBackfillService.BackfillResult(5, 3, 2, 1));

        AdminController controller = new AdminController(
                mock(NewsFetchService.class),
                mock(PlayerProfileBackfillService.class),
                mock(PlayerSquadBackfillService.class),
                playerSocialBackfillService,
                mock(FetchLogRepository.class),
                mock(NewsRepository.class),
                mock(DongqiudiProvider.class),
                List.of(),
                new ConcurrentMapCacheManager("socialPlayers")
        );

        ApiResponse<Map<String, Object>> response = controller.backfillPlayerSocial(50, List.of(5L));

        assertEquals(50, response.data().get("limit"));
        assertEquals(List.of(5L), response.data().get("teamIds"));
        assertEquals(5, response.data().get("scannedPlayers"));
        assertEquals(3, response.data().get("playersWithProfiles"));
        assertEquals(2, response.data().get("insertedProfiles"));
        assertEquals(1, response.data().get("updatedProfiles"));
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
