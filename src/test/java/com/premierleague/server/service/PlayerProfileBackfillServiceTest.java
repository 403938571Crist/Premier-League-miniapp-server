package com.premierleague.server.service;

import com.premierleague.server.entity.Player;
import com.premierleague.server.provider.PlPhotoProvider;
import com.premierleague.server.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerProfileBackfillServiceTest {

    @Test
    void backfillsMissingChineseNameAndPhotoAndClearsRelatedCaches() {
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        PlPhotoProvider plPhotoProvider = mock(PlPhotoProvider.class);
        ConcurrentMapCacheManager cacheManager =
                new ConcurrentMapCacheManager("teamSquad", "playerDetail", "playerByApiId");
        PlayerProfileBackfillService service =
                new PlayerProfileBackfillService(playerRepository, plPhotoProvider, cacheManager);

        Player player = new Player();
        player.setId(9L);
        player.setApiId(9L);
        player.setName("Erling Haaland");
        player.setTeamId(2L);

        cacheManager.getCache("teamSquad").put("2", "stale");
        cacheManager.getCache("playerDetail").put("9", "stale");
        cacheManager.getCache("playerByApiId").put("9", "stale");

        when(playerRepository.findPlayersNeedingProfileBackfill(any(Pageable.class))).thenReturn(List.of(player));
        when(plPhotoProvider.findUsablePhotoUrl("Erling Haaland", null)).thenReturn("https://img/haaland.png");
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlayerProfileBackfillService.BackfillResult result = service.backfillMissingProfiles(50);

        assertEquals(1, result.scanned());
        assertEquals(1, result.updatedPlayers());
        assertEquals(1, result.chineseNamesUpdated());
        assertEquals(1, result.photosUpdated());
        assertEquals("\u54c8\u5170\u5fb7", player.getChineseName());
        assertEquals("https://img/haaland.png", player.getPhotoUrl());
        assertNull(cacheManager.getCache("teamSquad").get("2"));
        assertNull(cacheManager.getCache("playerDetail").get("9"));
        assertNull(cacheManager.getCache("playerByApiId").get("9"));
        verify(playerRepository).save(player);
    }

    @Test
    void skipsSaveWhenNothingCanBeResolved() {
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        PlPhotoProvider plPhotoProvider = mock(PlPhotoProvider.class);
        PlayerProfileBackfillService service =
                new PlayerProfileBackfillService(playerRepository, plPhotoProvider,
                        new ConcurrentMapCacheManager("teamSquad", "playerDetail", "playerByApiId"));

        Player player = new Player();
        player.setId(33L);
        player.setApiId(33L);
        player.setName("Unknown Prospect");

        when(playerRepository.findPlayersNeedingProfileBackfill(any(Pageable.class))).thenReturn(List.of(player));
        when(plPhotoProvider.findUsablePhotoUrl("Unknown Prospect", null)).thenReturn(null);

        PlayerProfileBackfillService.BackfillResult result = service.backfillMissingProfiles(20);

        assertEquals(1, result.scanned());
        assertEquals(0, result.updatedPlayers());
        assertEquals(0, result.chineseNamesUpdated());
        assertEquals(0, result.photosUpdated());
        verify(playerRepository, never()).save(any(Player.class));
    }

    @Test
    void continuesBackfillWhenOnePlayerPhotoLookupFails() {
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        PlPhotoProvider plPhotoProvider = mock(PlPhotoProvider.class);
        PlayerProfileBackfillService service =
                new PlayerProfileBackfillService(playerRepository, plPhotoProvider,
                        new ConcurrentMapCacheManager("teamSquad", "playerDetail", "playerByApiId"));

        Player first = new Player();
        first.setId(1L);
        first.setApiId(1L);
        first.setName("Broken Lookup");

        Player second = new Player();
        second.setId(2L);
        second.setApiId(2L);
        second.setName("Ben White");

        when(playerRepository.findPlayersNeedingProfileBackfill(any(Pageable.class))).thenReturn(List.of(first, second));
        when(plPhotoProvider.findUsablePhotoUrl("Broken Lookup", null)).thenThrow(new RuntimeException("timeout"));
        when(plPhotoProvider.findUsablePhotoUrl("Ben White", null)).thenReturn("https://img/ben-white.png");
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlayerProfileBackfillService.BackfillResult result = service.backfillMissingProfiles(20);

        assertEquals(2, result.scanned());
        assertEquals(1, result.updatedPlayers());
        assertEquals(1, result.chineseNamesUpdated());
        assertEquals(1, result.photosUpdated());
        assertEquals("本·怀特", second.getChineseName());
        assertEquals("https://img/ben-white.png", second.getPhotoUrl());
        verify(playerRepository, times(1)).save(any(Player.class));
    }

    @Test
    void scopedBackfillFallsBackToEnglishNameWhenMapperMisses() {
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        PlPhotoProvider plPhotoProvider = mock(PlPhotoProvider.class);
        PlayerProfileBackfillService service =
                new PlayerProfileBackfillService(playerRepository, plPhotoProvider,
                        new ConcurrentMapCacheManager("teamSquad", "playerDetail", "playerByApiId"));

        Player player = new Player();
        player.setId(88L);
        player.setApiId(88L);
        player.setTeamId(5L);
        player.setName("Unknown Prospect");

        when(playerRepository.findPlayersNeedingProfileBackfillByTeamIds(eq(List.of(5L)), any(Pageable.class)))
                .thenReturn(List.of(player));
        when(plPhotoProvider.findUsablePhotoUrl("Unknown Prospect", null)).thenReturn(null);
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlayerProfileBackfillService.BackfillResult result =
                service.backfillMissingProfiles(20, List.of(5L));

        assertEquals(1, result.scanned());
        assertEquals(1, result.updatedPlayers());
        assertEquals(1, result.chineseNamesUpdated());
        assertEquals(0, result.photosUpdated());
        assertEquals("Unknown Prospect", player.getChineseName());
        verify(playerRepository).save(player);
    }

    @Test
    void reusesDuplicatePlayerPhotoFromSameTeamAndBirthDate() {
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        PlPhotoProvider plPhotoProvider = mock(PlPhotoProvider.class);
        PlayerProfileBackfillService service =
                new PlayerProfileBackfillService(playerRepository, plPhotoProvider,
                        new ConcurrentMapCacheManager("teamSquad", "playerDetail", "playerByApiId"));

        Player missing = new Player();
        missing.setId(95L);
        missing.setTeamId(3L);
        missing.setName("Chido Obi-Martin");
        missing.setDateOfBirth(LocalDate.of(2007, 11, 29));

        Player existing = new Player();
        existing.setId(421L);
        existing.setTeamId(3L);
        existing.setName("Chido Obi");
        existing.setDateOfBirth(LocalDate.of(2007, 11, 29));
        existing.setPhotoUrl("https://resources.premierleague.com/premierleague25/photos/players/110x140/596047.png");

        when(playerRepository.findPlayersNeedingProfileBackfill(any(Pageable.class))).thenReturn(List.of(missing));
        when(playerRepository.findByTeamIdAndDateOfBirthOrderByIdAsc(3L, LocalDate.of(2007, 11, 29)))
                .thenReturn(List.of(missing, existing));
        when(plPhotoProvider.findUsablePhotoUrl("Chido Obi-Martin", null)).thenReturn(null);
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlayerProfileBackfillService.BackfillResult result = service.backfillMissingProfiles(20);

        assertEquals(1, result.scanned());
        assertEquals(1, result.updatedPlayers());
        assertEquals(0, result.chineseNamesUpdated());
        assertEquals(1, result.photosUpdated());
        assertEquals(existing.getPhotoUrl(), missing.getPhotoUrl());
        verify(playerRepository).save(missing);
    }

    @Test
    void reusesDuplicatePhotoForNicknameAndTokenVariantsOnSameBirthDate() {
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        PlPhotoProvider plPhotoProvider = mock(PlPhotoProvider.class);
        PlayerProfileBackfillService service =
                new PlayerProfileBackfillService(playerRepository, plPhotoProvider,
                        new ConcurrentMapCacheManager("teamSquad", "playerDetail", "playerByApiId"));

        Player danielBentley = new Player();
        danielBentley.setId(348L);
        danielBentley.setTeamId(20L);
        danielBentley.setName("Daniel Bentley");
        danielBentley.setDateOfBirth(LocalDate.of(1993, 7, 13));

        Player danBentley = new Player();
        danBentley.setId(508L);
        danBentley.setTeamId(20L);
        danBentley.setName("Dan Bentley");
        danBentley.setDateOfBirth(LocalDate.of(1993, 7, 13));
        danBentley.setPhotoUrl("https://resources.premierleague.com/premierleague/photos/players/250x250/p79602.png");

        Player hwangHeechan = new Player();
        hwangHeechan.setId(372L);
        hwangHeechan.setTeamId(20L);
        hwangHeechan.setName("Hwang Heechan");
        hwangHeechan.setDateOfBirth(LocalDate.of(1996, 1, 26));

        Player hwangHeeChan = new Player();
        hwangHeeChan.setId(512L);
        hwangHeeChan.setTeamId(20L);
        hwangHeeChan.setName("Hwang Hee-Chan");
        hwangHeeChan.setDateOfBirth(LocalDate.of(1996, 1, 26));
        hwangHeeChan.setPhotoUrl("https://resources.premierleague.com/premierleague/photos/players/250x250/p184754.png");

        Player kevinSantos = new Player();
        kevinSantos.setId(379L);
        kevinSantos.setTeamId(12L);
        kevinSantos.setName("Kevin Santos");
        kevinSantos.setDateOfBirth(LocalDate.of(2003, 1, 4));

        Player kevin = new Player();
        kevin.setId(491L);
        kevin.setTeamId(12L);
        kevin.setName("Kevin");
        kevin.setDateOfBirth(LocalDate.of(2003, 1, 4));
        kevin.setPhotoUrl("https://resources.premierleague.com/premierleague25/photos/players/110x140/560552.png");

        Player louisBeyer = new Player();
        louisBeyer.setId(335L);
        louisBeyer.setTeamId(19L);
        louisBeyer.setName("Louis Beyer");
        louisBeyer.setDateOfBirth(LocalDate.of(2000, 5, 19));

        Player jordanBeyer = new Player();
        jordanBeyer.setId(505L);
        jordanBeyer.setTeamId(19L);
        jordanBeyer.setName("Jordan Beyer");
        jordanBeyer.setDateOfBirth(LocalDate.of(2000, 5, 19));
        jordanBeyer.setPhotoUrl("https://resources.premierleague.com/premierleague/photos/players/250x250/p241231.png");

        when(playerRepository.findPlayersNeedingProfileBackfill(any(Pageable.class)))
                .thenReturn(List.of(danielBentley, hwangHeechan, kevinSantos, louisBeyer));
        when(playerRepository.findByTeamIdAndDateOfBirthOrderByIdAsc(20L, LocalDate.of(1993, 7, 13)))
                .thenReturn(List.of(danielBentley, danBentley));
        when(playerRepository.findByTeamIdAndDateOfBirthOrderByIdAsc(20L, LocalDate.of(1996, 1, 26)))
                .thenReturn(List.of(hwangHeechan, hwangHeeChan));
        when(playerRepository.findByTeamIdAndDateOfBirthOrderByIdAsc(12L, LocalDate.of(2003, 1, 4)))
                .thenReturn(List.of(kevinSantos, kevin));
        when(playerRepository.findByTeamIdAndDateOfBirthOrderByIdAsc(19L, LocalDate.of(2000, 5, 19)))
                .thenReturn(List.of(louisBeyer, jordanBeyer));
        when(plPhotoProvider.findUsablePhotoUrl("Daniel Bentley", null)).thenReturn(null);
        when(plPhotoProvider.findUsablePhotoUrl("Hwang Heechan", null)).thenReturn(null);
        when(plPhotoProvider.findUsablePhotoUrl("Kevin Santos", null)).thenReturn(null);
        when(plPhotoProvider.findUsablePhotoUrl("Louis Beyer", null)).thenReturn(null);
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlayerProfileBackfillService.BackfillResult result = service.backfillMissingProfiles(20);

        assertEquals(4, result.scanned());
        assertEquals(4, result.updatedPlayers());
        assertEquals(0, result.chineseNamesUpdated());
        assertEquals(4, result.photosUpdated());
        assertAll(
                () -> assertEquals(danBentley.getPhotoUrl(), danielBentley.getPhotoUrl()),
                () -> assertEquals(hwangHeeChan.getPhotoUrl(), hwangHeechan.getPhotoUrl()),
                () -> assertEquals(kevin.getPhotoUrl(), kevinSantos.getPhotoUrl()),
                () -> assertEquals(jordanBeyer.getPhotoUrl(), louisBeyer.getPhotoUrl())
        );
        verify(playerRepository, times(4)).save(any(Player.class));
    }
}
