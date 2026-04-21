package com.premierleague.server.provider;

import com.premierleague.server.util.HttpClientUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PulseliveSquadProviderTest {

    private HttpClientUtil httpClientUtil;
    private PulseliveSquadProvider provider;

    @BeforeEach
    void setUp() {
        httpClientUtil = mock(HttpClientUtil.class);
        provider = new PulseliveSquadProvider(httpClientUtil);
    }

    @Test
    void fetchSquadResolvesTeamIdAndParsesPlayers() {
        when(httpClientUtil.getWithHeaders(contains("/v1/competitions/8/seasons/2025/teams?_limit=50"), anyMap()))
                .thenReturn("""
                        {
                          "data": [
                            {"id":"14","name":"Liverpool","shortName":"Liverpool"},
                            {"id":"8","name":"Chelsea","shortName":"Chelsea"}
                          ]
                        }
                        """);
        when(httpClientUtil.getWithHeaders(contains("/v2/competitions/8/seasons/2025/teams/14/squad"), anyMap()))
                .thenReturn("""
                        {
                          "team": {"id":"14","name":"Liverpool"},
                          "players": [
                            {
                              "id":"492777",
                              "name":{"display":"Conor Bradley","first":"Conor","last":"Bradley"},
                              "shirtNum":12,
                              "position":"Defender",
                              "country":{"country":"Northern Ireland"},
                              "dates":{"birth":"2003-07-09"},
                              "height":180,
                              "weight":64,
                              "preferredFoot":"Right"
                            },
                            {
                              "id":"464353",
                              "name":{"display":"Harvey Davies","first":"Harvey","last":"Davies"},
                              "shirtNum":95,
                              "position":"Goalkeeper",
                              "country":{"country":"England"},
                              "dates":{"birth":"2003-09-03"},
                              "height":190,
                              "preferredFoot":"Left"
                            }
                          ]
                        }
                        """);

        List<PulseliveSquadProvider.SquadPlayer> squad = provider.fetchSquad("Liverpool FC");

        assertEquals(2, squad.size());
        PulseliveSquadProvider.SquadPlayer first = squad.get(0);
        assertEquals("492777", first.officialId());
        assertEquals("Conor Bradley", first.displayName());
        assertEquals("12", first.shirtNumber());
        assertEquals("Defender", first.position());
        assertEquals("Northern Ireland", first.nationality());
        assertEquals(LocalDate.of(2003, 7, 9), first.dateOfBirth());
        assertEquals(180, first.heightCm());
        assertEquals(64, first.weightKg());
        assertEquals("Right", first.preferredFoot());
    }

    @Test
    void fetchSquadMatchesViaShortNameAlias() {
        when(httpClientUtil.getWithHeaders(contains("/v1/competitions/8/seasons/2025/teams?_limit=50"), anyMap()))
                .thenReturn("""
                        {
                          "data": [
                            {"id":"8","name":"Chelsea","shortName":"Chelsea"}
                          ]
                        }
                        """);
        when(httpClientUtil.getWithHeaders(contains("/v2/competitions/8/seasons/2025/teams/8/squad"), anyMap()))
                .thenReturn("""
                        {"team":{"id":"8","name":"Chelsea"},"players":[]}
                        """);

        List<PulseliveSquadProvider.SquadPlayer> squad = provider.fetchSquad("Chelsea FC");

        assertTrue(squad.isEmpty());
    }
}
