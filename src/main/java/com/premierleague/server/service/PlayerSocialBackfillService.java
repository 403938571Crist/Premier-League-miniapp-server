package com.premierleague.server.service;

import com.premierleague.server.entity.Player;
import com.premierleague.server.entity.PlayerSocial;
import com.premierleague.server.entity.Team;
import com.premierleague.server.model.PlayerSocialCandidate;
import com.premierleague.server.provider.WikidataPlayerSocialProvider;
import com.premierleague.server.repository.PlayerRepository;
import com.premierleague.server.repository.PlayerSocialRepository;
import com.premierleague.server.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerSocialBackfillService {

    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;
    private final PlayerSocialRepository playerSocialRepository;
    private final WikidataPlayerSocialProvider wikidataPlayerSocialProvider;
    private final CacheManager cacheManager;

    @Transactional
    public BackfillResult backfillPlayerSocials(int limit, List<Long> teamIds) {
        List<Player> players = fetchPlayers(limit, teamIds);
        Map<Long, String> teamNames = loadTeamNames(players, teamIds);

        int playersWithProfiles = 0;
        int insertedProfiles = 0;
        int updatedProfiles = 0;

        for (Player player : players) {
            List<PlayerSocialCandidate> candidates = wikidataPlayerSocialProvider.fetchProfiles(player.getName());
            if (candidates.isEmpty()) {
                continue;
            }

            playersWithProfiles++;
            String teamName = teamNames.getOrDefault(player.getTeamId(), "");
            for (PlayerSocialCandidate candidate : candidates) {
                var existing = playerSocialRepository.findByPlayerIdAndPlatformIgnoreCase(player.getId(), candidate.platform());
                if (existing.isPresent()) {
                    if (merge(existing.get(), candidate, teamName)) {
                        playerSocialRepository.save(existing.get());
                        updatedProfiles++;
                    }
                } else {
                    PlayerSocial created = PlayerSocial.builder()
                            .id(buildId(player.getId(), candidate.platform()))
                            .playerId(player.getId())
                            .playerName(player.getName())
                            .teamId(player.getTeamId())
                            .teamName(teamName)
                            .platform(candidate.platform())
                            .handle(candidate.handle())
                            .profileUrl(candidate.profileUrl())
                            .avatar(candidate.avatar())
                            .verified(candidate.verified())
                            .summary(candidate.summary())
                            .build();
                    playerSocialRepository.save(created);
                    insertedProfiles++;
                }
            }
        }

        if (insertedProfiles + updatedProfiles > 0) {
            clearCache("socialPlayers");
        }

        BackfillResult result = new BackfillResult(players.size(), playersWithProfiles, insertedProfiles, updatedProfiles);
        log.info("[PlayerSocialBackfillService] backfill result: {}", result);
        return result;
    }

    private List<Player> fetchPlayers(int limit, List<Long> teamIds) {
        int batchSize = Math.max(1, limit);
        if (teamIds != null && !teamIds.isEmpty()) {
            return playerRepository.findByTeamIdInOrderByTeamIdAscNameAsc(teamIds).stream()
                    .limit(batchSize)
                    .toList();
        }
        return playerRepository.findAll(PageRequest.of(0, batchSize)).getContent();
    }

    private Map<Long, String> loadTeamNames(List<Player> players, List<Long> requestedTeamIds) {
        List<Long> teamIds = requestedTeamIds != null && !requestedTeamIds.isEmpty()
                ? requestedTeamIds
                : players.stream().map(Player::getTeamId).distinct().toList();
        List<Team> teams = teamRepository.findAllById(teamIds);
        Map<Long, String> teamNames = new HashMap<>();
        for (Team team : teams) {
            teamNames.put(team.getId(), team.getName());
        }
        return teamNames;
    }

    private boolean merge(PlayerSocial existing, PlayerSocialCandidate candidate, String teamName) {
        boolean changed = false;
        if (!candidate.handle().equals(existing.getHandle())) {
            existing.setHandle(candidate.handle());
            changed = true;
        }
        if (!candidate.profileUrl().equals(existing.getProfileUrl())) {
            existing.setProfileUrl(candidate.profileUrl());
            changed = true;
        }
        if (candidate.avatar() != null && !candidate.avatar().equals(existing.getAvatar())) {
            existing.setAvatar(candidate.avatar());
            changed = true;
        }
        if (candidate.verified() != null && !candidate.verified().equals(existing.getVerified())) {
            existing.setVerified(candidate.verified());
            changed = true;
        }
        if (candidate.summary() != null && !candidate.summary().equals(existing.getSummary())) {
            existing.setSummary(candidate.summary());
            changed = true;
        }
        if (teamName != null && !teamName.equals(existing.getTeamName())) {
            existing.setTeamName(teamName);
            changed = true;
        }
        return changed;
    }

    private String buildId(Long playerId, String platform) {
        return "player-" + playerId + "-" + platform.toLowerCase().replaceAll("[^a-z0-9]+", "-");
    }

    private void clearCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }

    public record BackfillResult(
            int scannedPlayers,
            int playersWithProfiles,
            int insertedProfiles,
            int updatedProfiles
    ) {
    }
}
