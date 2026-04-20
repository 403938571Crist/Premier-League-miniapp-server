package com.premierleague.server.service;

import com.premierleague.server.entity.Player;
import com.premierleague.server.entity.Team;
import com.premierleague.server.model.PlayerStat;
import com.premierleague.server.provider.FootballDataProvider;
import com.premierleague.server.provider.PlPhotoProvider;
import com.premierleague.server.provider.PulseliveProvider;
import com.premierleague.server.repository.MatchRepository;
import com.premierleague.server.repository.PlayerRepository;
import com.premierleague.server.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TeamServiceTest {

    private TeamRepository teamRepository;
    private PlayerRepository playerRepository;
    private MatchRepository matchRepository;
    private FootballDataProvider footballDataProvider;
    private PlPhotoProvider plPhotoProvider;
    private PulseliveProvider pulseliveProvider;
    private SqlCacheService sqlCacheService;
    private TeamService teamService;

    @BeforeEach
    void setUp() {
        teamRepository = mock(TeamRepository.class);
        playerRepository = mock(PlayerRepository.class);
        matchRepository = mock(MatchRepository.class);
        footballDataProvider = mock(FootballDataProvider.class);
        plPhotoProvider = mock(PlPhotoProvider.class);
        pulseliveProvider = mock(PulseliveProvider.class);
        sqlCacheService = mock(SqlCacheService.class);
        teamService = new TeamService(
                teamRepository,
                playerRepository,
                matchRepository,
                footballDataProvider,
                plPhotoProvider,
                pulseliveProvider,
                sqlCacheService
        );
    }

    @Test
    void groupsDetailedPositionsAndReturnsForwardsAlias() {
        Team team = Team.builder()
                .id(6L)
                .apiId(73L)
                .name("Tottenham Hotspur FC")
                .shortName("Spurs")
                .build();

        when(teamRepository.findById(73L)).thenReturn(Optional.empty());
        when(teamRepository.findByApiId(73L)).thenReturn(Optional.of(team));
        when(playerRepository.findByTeamIdOrderByPositionAscShirtNumberAsc(73L)).thenReturn(List.of());
        when(playerRepository.findByTeamIdOrderByPositionAscShirtNumberAsc(6L)).thenReturn(List.of(
                player(1L, "Guglielmo Vicario", "Goalkeeper", "1"),
                player(2L, "Cristian Romero", "Centre-Back", "17"),
                player(3L, "Pedro Porro", "Wing-Back", "23"),
                player(4L, "Pape Sarr", "Central Midfield", "29"),
                player(5L, "Son Heung-min", "Offence", "7")
        ));

        Map<String, Object> squad = teamService.getTeamSquad(73L);

        assertEquals(1, players(squad, "goalkeepers").size());
        assertEquals(2, players(squad, "defenders").size());
        assertEquals(1, players(squad, "midfielders").size());
        assertEquals(1, players(squad, "attackers").size());
        assertEquals(1, players(squad, "forwards").size());
        assertEquals(5, squad.get("totalCount"));
        assertTrue(players(squad, "others").isEmpty());

        verifyNoInteractions(footballDataProvider, pulseliveProvider);
    }

    @Test
    void enrichesDisplayLabelsAndPhotoFallback() {
        Team team = Team.builder()
                .id(1L)
                .apiId(57L)
                .name("Arsenal FC")
                .shortName("Arsenal")
                .build();

        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
        when(playerRepository.findByTeamIdOrderByPositionAscShirtNumberAsc(1L)).thenReturn(List.of(
                player(20L, "Aleksei Fedorushchenko", "Goalkeeper", "1", "Russia"),
                player(41L, "Ben White", "Right-Back", "4", "England")
        ));
        when(plPhotoProvider.findUsablePhotoUrl("Aleksei Fedorushchenko", null))
                .thenReturn("https://img/aleksei.png");
        when(plPhotoProvider.findUsablePhotoUrl("Ben White", null))
                .thenReturn("https://img/ben-white.png");

        Map<String, Object> squad = teamService.getTeamSquad(1L);

        Player goalkeeper = players(squad, "goalkeepers").get(0);
        Player defender = players(squad, "defenders").get(0);

        assertEquals("俄罗斯", goalkeeper.getNationalityLabel());
        assertEquals("https://img/aleksei.png", goalkeeper.getPhotoUrl());
        assertEquals("右后卫", defender.getPositionLabel());
        assertEquals("https://img/ben-white.png", defender.getPhotoUrl());
    }

    @Test
    void fallsBackToPulseliveWhenDbAndFootballDataAreEmpty() {
        Team team = Team.builder()
                .id(6L)
                .apiId(73L)
                .name("Tottenham Hotspur FC")
                .shortName("Spurs")
                .build();

        when(teamRepository.findById(73L)).thenReturn(Optional.empty());
        when(teamRepository.findByApiId(73L)).thenReturn(Optional.of(team));
        when(playerRepository.findByTeamIdOrderByPositionAscShirtNumberAsc(73L)).thenReturn(List.of());
        when(playerRepository.findByTeamIdOrderByPositionAscShirtNumberAsc(6L)).thenReturn(List.of());
        when(footballDataProvider.fetchTeamSquad(73L)).thenReturn(List.of());
        when(pulseliveProvider.fetchScorers()).thenReturn(List.of(
                new PlayerStat(null, 101L, "Son Heung-min", null, "Korea Republic",
                        "Attacker", "Forward", 7,
                        1001L, "Tottenham Hotspur", "Spurs", null, null,
                        17, 9, 0, 30, "https://img/son.png"),
                new PlayerStat(null, 202L, "Bukayo Saka", null, "England",
                        "Attacker", "Forward", 7,
                        1002L, "Arsenal", "Arsenal", null, null,
                        12, 10, 0, 28, "https://img/saka.png")
        ));

        Map<String, Object> squad = teamService.getTeamSquad(73L);

        assertEquals(1, players(squad, "attackers").size());
        assertEquals("Son Heung-min", players(squad, "attackers").get(0).getName());
        assertEquals(1, squad.get("totalCount"));

        verify(footballDataProvider).fetchTeamSquad(73L);
        verify(pulseliveProvider).fetchScorers();
    }

    @SuppressWarnings("unchecked")
    private List<Player> players(Map<String, Object> squad, String key) {
        return (List<Player>) squad.get(key);
    }

    private Player player(Long apiId, String name, String position, String shirtNumber) {
        Player player = new Player();
        player.setApiId(apiId);
        player.setName(name);
        player.setPosition(position);
        player.setShirtNumber(shirtNumber);
        return player;
    }

    private Player player(Long apiId, String name, String position, String shirtNumber, String nationality) {
        Player player = player(apiId, name, position, shirtNumber);
        player.setNationality(nationality);
        return player;
    }
}
