package com.premierleague.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.premierleague.server.model.PlayerStat;
import com.premierleague.server.provider.ApiFootballProvider;
import com.premierleague.server.provider.FbrefProvider;
import com.premierleague.server.provider.FootballDataProvider;
import com.premierleague.server.provider.PlPhotoProvider;
import com.premierleague.server.provider.PulseliveProvider;
import com.premierleague.server.provider.UnderstatProvider;
import com.premierleague.server.repository.PlayerRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerServiceTest {

    @Test
    void topScorersPrefersUnderstatBeforeFootballData() {
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        FootballDataProvider footballDataProvider = mock(FootballDataProvider.class);
        ApiFootballProvider apiFootballProvider = mock(ApiFootballProvider.class);
        UnderstatProvider understatProvider = mock(UnderstatProvider.class);
        PulseliveProvider pulseliveProvider = mock(PulseliveProvider.class);
        FbrefProvider fbrefProvider = mock(FbrefProvider.class);
        PlPhotoProvider plPhotoProvider = mock(PlPhotoProvider.class);
        SqlCacheService sqlCache = mock(SqlCacheService.class);

        PlayerService service = new PlayerService(
                playerRepository,
                footballDataProvider,
                apiFootballProvider,
                understatProvider,
                pulseliveProvider,
                fbrefProvider,
                plPhotoProvider,
                sqlCache
        );

        PlayerStat understatRow = stat("Erling Haaland", 23, 7, "https://img/haaland.png");

        when(sqlCache.get(eq("topScorers:5"), any(TypeReference.class))).thenReturn(Optional.empty());
        when(apiFootballProvider.fetchScorers()).thenReturn(List.of());
        when(understatProvider.fetchScorers()).thenReturn(List.of(understatRow));
        when(plPhotoProvider.findUsablePhotoUrl("Erling Haaland", "https://img/haaland.png"))
                .thenReturn("https://img/haaland.png");

        List<PlayerStat> result = service.getTopScorers(5);

        assertEquals(1, result.size());
        assertEquals("Erling Haaland", result.get(0).playerName());
        assertEquals(1, result.get(0).rank());
        verify(apiFootballProvider).fetchScorers();
        verify(understatProvider).fetchScorers();
        verify(footballDataProvider, never()).fetchScorers(5);
        verify(sqlCache).set(eq("topScorers:5"), any(), eq(Duration.ofHours(2)));
    }

    @Test
    void topAssistsPrefersUnderstatBeforeFootballData() {
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        FootballDataProvider footballDataProvider = mock(FootballDataProvider.class);
        ApiFootballProvider apiFootballProvider = mock(ApiFootballProvider.class);
        UnderstatProvider understatProvider = mock(UnderstatProvider.class);
        PulseliveProvider pulseliveProvider = mock(PulseliveProvider.class);
        FbrefProvider fbrefProvider = mock(FbrefProvider.class);
        PlPhotoProvider plPhotoProvider = mock(PlPhotoProvider.class);
        SqlCacheService sqlCache = mock(SqlCacheService.class);

        PlayerService service = new PlayerService(
                playerRepository,
                footballDataProvider,
                apiFootballProvider,
                understatProvider,
                pulseliveProvider,
                fbrefProvider,
                plPhotoProvider,
                sqlCache
        );

        PlayerStat understatRow = stat("Bruno Fernandes", 8, 18, "https://img/bruno.png");

        when(sqlCache.get(eq("topAssists:5"), any(TypeReference.class))).thenReturn(Optional.empty());
        when(apiFootballProvider.fetchAssists()).thenReturn(List.of());
        when(understatProvider.fetchScorers()).thenReturn(List.of(understatRow));
        when(plPhotoProvider.findUsablePhotoUrl("Bruno Fernandes", "https://img/bruno.png"))
                .thenReturn("https://img/bruno.png");

        List<PlayerStat> result = service.getTopAssists(5);

        assertEquals(1, result.size());
        assertEquals("Bruno Fernandes", result.get(0).playerName());
        assertEquals(1, result.get(0).rank());
        verify(apiFootballProvider).fetchAssists();
        verify(understatProvider).fetchScorers();
        verify(footballDataProvider, never()).fetchScorers(10);
        verify(sqlCache).set(eq("topAssists:5"), any(), eq(Duration.ofHours(2)));
    }

    private PlayerStat stat(String name, int goals, int assists, String photoUrl) {
        return new PlayerStat(
                null,
                1L,
                name,
                name,
                null,
                "Attacker",
                "前锋",
                null,
                null,
                "Manchester City",
                "Manchester City",
                "曼城",
                null,
                goals,
                assists,
                0,
                30,
                photoUrl
        );
    }
}
