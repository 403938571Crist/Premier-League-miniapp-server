package com.premierleague.server.service;

import com.premierleague.server.entity.Player;
import com.premierleague.server.entity.Team;
import com.premierleague.server.provider.PlPhotoProvider;
import com.premierleague.server.provider.PulseliveSquadProvider;
import com.premierleague.server.repository.PlayerRepository;
import com.premierleague.server.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerSquadBackfillServiceTest {

    @Test
    void backfillOfficialSquadsUpdatesExistingPlayersAndInsertsMissingPlayers() {
        TeamRepository teamRepository = mock(TeamRepository.class);
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        PulseliveSquadProvider pulseliveSquadProvider = mock(PulseliveSquadProvider.class);
        PlPhotoProvider plPhotoProvider = mock(PlPhotoProvider.class);
        ConcurrentMapCacheManager cacheManager =
                new ConcurrentMapCacheManager("teamSquad", "playerDetail", "playerByApiId");

        PlayerSquadBackfillService service = new PlayerSquadBackfillService(
                teamRepository,
                playerRepository,
                pulseliveSquadProvider,
                plPhotoProvider,
                cacheManager
        );

        Team liverpool = new Team();
        liverpool.setId(5L);
        liverpool.setName("Liverpool FC");

        Player existing = new Player();
        existing.setId(1L);
        existing.setTeamId(5L);
        existing.setName("Conor Bradley");

        cacheManager.getCache("teamSquad").put("5", "stale");

        when(teamRepository.findAllById(List.of(5L))).thenReturn(List.of(liverpool));
        when(playerRepository.findByTeamIdOrderByPositionAscShirtNumberAsc(5L)).thenReturn(List.of(existing));
        when(pulseliveSquadProvider.fetchSquad("Liverpool FC")).thenReturn(List.of(
                new PulseliveSquadProvider.SquadPlayer(
                        "492777",
                        "Conor Bradley",
                        "12",
                        "Defender",
                        "Northern Ireland",
                        LocalDate.of(2003, 7, 9),
                        180,
                        64,
                        "Right"
                ),
                new PulseliveSquadProvider.SquadPlayer(
                        "464353",
                        "Harvey Davies",
                        "95",
                        "Goalkeeper",
                        "England",
                        LocalDate.of(2003, 9, 3),
                        190,
                        77,
                        "Left"
                )
        ));
        when(plPhotoProvider.findPhotoUrlByOfficialId("492777")).thenReturn("https://img/conor.png");
        when(plPhotoProvider.findPhotoUrlByOfficialId("464353")).thenReturn("https://img/harvey.png");
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlayerSquadBackfillService.BackfillResult result = service.backfillOfficialSquads(List.of(5L));

        assertEquals(1, result.scannedTeams());
        assertEquals(2, result.scannedPlayers());
        assertEquals(1, result.createdPlayers());
        assertEquals(1, result.updatedPlayers());
        assertEquals(2, result.photosUpdated());

        assertEquals("https://img/conor.png", existing.getPhotoUrl());
        assertEquals("12", existing.getShirtNumber());
        assertEquals("Defender", existing.getPosition());
        assertEquals(LocalDate.of(2003, 7, 9), existing.getDateOfBirth());
        assertNull(cacheManager.getCache("teamSquad").get("5"));
        verify(playerRepository, times(2)).save(any(Player.class));
    }

    @Test
    void backfillOfficialSquadsMatchesCurlyApostrophesAgainstOfficialNames() {
        TeamRepository teamRepository = mock(TeamRepository.class);
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        PulseliveSquadProvider pulseliveSquadProvider = mock(PulseliveSquadProvider.class);
        PlPhotoProvider plPhotoProvider = mock(PlPhotoProvider.class);

        PlayerSquadBackfillService service = new PlayerSquadBackfillService(
                teamRepository,
                playerRepository,
                pulseliveSquadProvider,
                plPhotoProvider,
                new ConcurrentMapCacheManager("teamSquad", "playerDetail", "playerByApiId")
        );

        Team arsenal = new Team();
        arsenal.setId(1L);
        arsenal.setName("Arsenal FC");

        Player existing = new Player();
        existing.setId(20L);
        existing.setTeamId(1L);
        existing.setName("Ceadach O’Neill");

        when(teamRepository.findAllById(List.of(1L))).thenReturn(List.of(arsenal));
        when(playerRepository.findByTeamIdOrderByPositionAscShirtNumberAsc(1L)).thenReturn(List.of(existing));
        when(pulseliveSquadProvider.fetchSquad("Arsenal FC")).thenReturn(List.of(
                new PulseliveSquadProvider.SquadPlayer(
                        "777777",
                        "Ceadach O'Neill",
                        "62",
                        "Midfielder",
                        "England",
                        null,
                        null,
                        null,
                        null
                )
        ));
        when(plPhotoProvider.findPhotoUrlByOfficialId("777777")).thenReturn("https://img/oneill.png");
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlayerSquadBackfillService.BackfillResult result = service.backfillOfficialSquads(List.of(1L));

        assertEquals(1, result.updatedPlayers());
        assertEquals("https://img/oneill.png", existing.getPhotoUrl());
        assertEquals("62", existing.getShirtNumber());
    }
}
