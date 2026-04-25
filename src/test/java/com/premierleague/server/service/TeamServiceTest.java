package com.premierleague.server.service;

import com.premierleague.server.entity.Player;
import com.premierleague.server.entity.Team;
import com.premierleague.server.model.PlayerStat;
import com.premierleague.server.provider.FootballDataProvider;
import com.premierleague.server.provider.PlPhotoProvider;
import com.premierleague.server.provider.PulseliveStandingsProvider;
import com.premierleague.server.repository.MatchRepository;
import com.premierleague.server.repository.PlayerRepository;
import com.premierleague.server.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TeamServiceTest {

    private TeamRepository teamRepository;
    private PlayerRepository playerRepository;
    private MatchRepository matchRepository;
    private FootballDataProvider footballDataProvider;
    private PulseliveStandingsProvider pulseliveStandingsProvider;
    private PlPhotoProvider plPhotoProvider;
    private PlayerService playerService;
    private SqlCacheService sqlCacheService;
    private TeamService teamService;

    @BeforeEach
    void setUp() {
        teamRepository = mock(TeamRepository.class);
        playerRepository = mock(PlayerRepository.class);
        matchRepository = mock(MatchRepository.class);
        footballDataProvider = mock(FootballDataProvider.class);
        pulseliveStandingsProvider = mock(PulseliveStandingsProvider.class);
        plPhotoProvider = mock(PlPhotoProvider.class);
        playerService = mock(PlayerService.class);
        sqlCacheService = mock(SqlCacheService.class);
        teamService = new TeamService(
                teamRepository,
                playerRepository,
                matchRepository,
                footballDataProvider,
                pulseliveStandingsProvider,
                plPhotoProvider,
                playerService,
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

        verifyNoInteractions(footballDataProvider, playerService, plPhotoProvider);
    }

    @Test
    void enrichesDisplayLabelsWithoutRealtimePhotoLookup() {
        Team team = Team.builder()
                .id(1L)
                .apiId(57L)
                .name("Arsenal FC")
                .shortName("Arsenal")
                .build();

        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
        when(playerRepository.findByTeamIdOrderByPositionAscShirtNumberAsc(1L)).thenReturn(List.of(
                player(20L, "Aleksei Fedorushchenko", "Goalkeeper", "1", "Russia", null),
                player(41L, "Ben White", "Right-Back", "4", "England", "https://img/ben-white.png")
        ));

        Map<String, Object> squad = teamService.getTeamSquad(1L);

        Player goalkeeper = players(squad, "goalkeepers").get(0);
        Player defender = players(squad, "defenders").get(0);

        assertEquals("俄罗斯", goalkeeper.getNationalityLabel());
        assertNull(goalkeeper.getPhotoUrl());
        assertEquals("右后卫", defender.getPositionLabel());
        assertEquals("https://img/ben-white.png", defender.getPhotoUrl());
        verifyNoInteractions(plPhotoProvider);
    }

    @Test
    void dedupesCanonicalAndLegacySquadRowsForSamePlayer() {
        Team team = Team.builder()
                .id(3L)
                .apiId(66L)
                .name("Manchester United FC")
                .shortName("Man Utd")
                .build();

        Player canonical = player(null, "Altay Bayindir", "Goalkeeper", "1", "Turkey", null);
        canonical.setId(419L);
        canonical.setTeamId(3L);
        canonical.setDateOfBirth(LocalDate.of(1998, 4, 14));

        Player legacy = player(2L, "Altay Bayındır", "Goalkeeper", "1", "Turkey", "https://img/altay.png");
        legacy.setId(2L);
        legacy.setTeamId(66L);
        legacy.setChineseName("巴因迪尔");
        legacy.setDateOfBirth(LocalDate.of(1998, 4, 14));

        when(teamRepository.findById(3L)).thenReturn(Optional.of(team));
        when(playerRepository.findByTeamIdOrderByPositionAscShirtNumberAsc(3L)).thenReturn(List.of(canonical));
        when(playerRepository.findByTeamIdOrderByPositionAscShirtNumberAsc(66L)).thenReturn(List.of(legacy));

        Map<String, Object> squad = teamService.getTeamSquad(3L);

        List<Player> goalkeepers = players(squad, "goalkeepers");
        assertEquals(1, goalkeepers.size());
        assertEquals(1, squad.get("totalCount"));
        assertEquals("Altay Bayindir", goalkeepers.get(0).getName());
        assertEquals("巴因迪尔", goalkeepers.get(0).getChineseName());
        assertEquals("https://img/altay.png", goalkeepers.get(0).getPhotoUrl());
    }

    @Test
        void fallsBackToScorerChainWhenDbAndFootballDataAreEmpty() {
        Team team = Team.builder()
                .id(6L)
                .apiId(73L)
                .name("Tottenham Hotspur FC")
                .shortName("Spurs")
                .build();

        when(teamRepository.findById(73L)).thenReturn(Optional.empty());
        when(teamRepository.findByApiId(73L)).thenReturn(Optional.of(team));
        when(playerRepository.findByTeamIdOrderByPositionAscShirtNumberAsc(73L)).thenReturn(List.of());
        when(playerRepository.findByTeamIdOrderByPositionAscShirtNumberAsc(6L))
                .thenReturn(List.of())
                .thenReturn(List.of(player(101L, "Son Heung-min", "Attacker", "7", "Korea Republic", "https://img/son.png")));
        when(footballDataProvider.fetchTeamSquad(73L)).thenReturn(List.of());
        when(playerService.getTopScorers(100)).thenReturn(List.of(
                new PlayerStat(null, 101L, "Son Heung-min", null, "Korea Republic",
                        "Attacker", "Forward", 7,
                        1001L, "Tottenham Hotspur", "Spurs", null, null,
                        17, 9, 0, 30, "https://img/son.png"),
                new PlayerStat(null, 202L, "Bukayo Saka", null, "England",
                        "Attacker", "Forward", 7,
                        1002L, "Arsenal", "Arsenal", null, null,
                        12, 10, 0, 28, "https://img/saka.png")
        ));
        when(playerRepository.findByApiId(101L)).thenReturn(Optional.empty());
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> squad = teamService.getTeamSquad(73L);

        assertEquals(1, players(squad, "attackers").size());
        assertEquals("Son Heung-min", players(squad, "attackers").get(0).getName());
        assertEquals(1, squad.get("totalCount"));
        assertFalse(squad.containsKey("all"));

        verify(footballDataProvider).fetchTeamSquad(73L);
        verify(playerService).getTopScorers(100);
        verify(playerRepository).save(argThat(player ->
                player.getApiId().equals(101L)
                        && player.getTeamId().equals(6L)
                        && "https://img/son.png".equals(player.getPhotoUrl())
        ));
        verify(playerRepository, never()).save(argThat(player ->
                player.getApiId() != null && player.getApiId().equals(202L)
        ));
    }

    @Test
    void savePlayerPreservesExistingBackfilledFieldsWhenIncomingValuesAreBlank() {
        Player existing = player(20L, "Bukayo Saka", "Right Winger", "7", "England", "https://img/saka.png");
        existing.setChineseName("\u8428\u5361");
        existing.setChinesePosition("\u53f3\u8fb9\u950b");
        existing.setTeamId(1L);

        Player incoming = player(20L, "Bukayo Saka", "Right Winger", "7", "England", null);
        incoming.setTeamId(1L);

        when(playerRepository.findByApiId(20L)).thenReturn(Optional.of(existing));
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Player saved = teamService.savePlayer(incoming);

        assertEquals("\u8428\u5361", saved.getChineseName());
        assertEquals("\u53f3\u8fb9\u950b", saved.getChinesePosition());
        assertEquals("https://img/saka.png", saved.getPhotoUrl());
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

    private Player player(Long apiId, String name, String position, String shirtNumber, String nationality, String photoUrl) {
        Player player = player(apiId, name, position, shirtNumber);
        player.setNationality(nationality);
        player.setPhotoUrl(photoUrl);
        return player;
    }
}
