package com.premierleague.server.service;

import com.premierleague.server.entity.Player;
import com.premierleague.server.entity.PlayerSocial;
import com.premierleague.server.entity.Team;
import com.premierleague.server.model.PlayerSocialCandidate;
import com.premierleague.server.provider.WikidataPlayerSocialProvider;
import com.premierleague.server.repository.PlayerRepository;
import com.premierleague.server.repository.PlayerSocialRepository;
import com.premierleague.server.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerSocialBackfillServiceTest {

    @Test
    void backfillPlayerSocialsUpsertsProfilesAndClearsCache() {
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        TeamRepository teamRepository = mock(TeamRepository.class);
        PlayerSocialRepository playerSocialRepository = mock(PlayerSocialRepository.class);
        WikidataPlayerSocialProvider provider = mock(WikidataPlayerSocialProvider.class);
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager("socialPlayers");

        PlayerSocialBackfillService service = new PlayerSocialBackfillService(
                playerRepository,
                teamRepository,
                playerSocialRepository,
                provider,
                cacheManager
        );

        Team liverpool = new Team();
        liverpool.setId(5L);
        liverpool.setName("Liverpool FC");

        Player salah = new Player();
        salah.setId(11L);
        salah.setTeamId(5L);
        salah.setName("Mohamed Salah");

        PlayerSocial existingX = PlayerSocial.builder()
                .id("player-11-x")
                .playerId(11L)
                .teamId(5L)
                .teamName("Liverpool FC")
                .playerName("Mohamed Salah")
                .platform("X")
                .handle("@old")
                .profileUrl("https://x.com/old")
                .build();

        cacheManager.getCache("socialPlayers").put("all", "stale");

        when(playerRepository.findByTeamIdInOrderByTeamIdAscNameAsc(List.of(5L))).thenReturn(List.of(salah));
        when(teamRepository.findAllById(List.of(5L))).thenReturn(List.of(liverpool));
        when(provider.fetchProfiles("Mohamed Salah")).thenReturn(List.of(
                new PlayerSocialCandidate("X", "@MoSalah", "https://x.com/MoSalah", null, null, null),
                new PlayerSocialCandidate("Instagram", "@mosalah", "https://www.instagram.com/mosalah/", null, null, null)
        ));
        when(playerSocialRepository.findByPlayerIdAndPlatformIgnoreCase(11L, "X")).thenReturn(Optional.of(existingX));
        when(playerSocialRepository.findByPlayerIdAndPlatformIgnoreCase(11L, "Instagram")).thenReturn(Optional.empty());
        when(playerSocialRepository.save(any(PlayerSocial.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlayerSocialBackfillService.BackfillResult result =
                service.backfillPlayerSocials(50, List.of(5L));

        assertEquals(1, result.scannedPlayers());
        assertEquals(1, result.playersWithProfiles());
        assertEquals(1, result.insertedProfiles());
        assertEquals(1, result.updatedProfiles());
        assertEquals("@MoSalah", existingX.getHandle());
        assertEquals("https://x.com/MoSalah", existingX.getProfileUrl());
        assertNull(cacheManager.getCache("socialPlayers").get("all"));
        verify(playerSocialRepository, times(2)).save(any(PlayerSocial.class));
    }
}
