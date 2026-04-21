package com.premierleague.server.service;

import com.premierleague.server.entity.Player;
import com.premierleague.server.entity.Team;
import com.premierleague.server.provider.PlPhotoProvider;
import com.premierleague.server.provider.PulseliveSquadProvider;
import com.premierleague.server.repository.PlayerRepository;
import com.premierleague.server.repository.TeamRepository;
import com.premierleague.server.util.PlayerChineseNameMapper;
import com.premierleague.server.util.PlayerNameNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerSquadBackfillService {

    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final PulseliveSquadProvider pulseliveSquadProvider;
    private final PlPhotoProvider plPhotoProvider;
    private final CacheManager cacheManager;

    @Transactional
    public BackfillResult backfillOfficialSquads(List<Long> teamIds) {
        List<Team> teams = teamRepository.findAllById(teamIds);

        int scannedPlayers = 0;
        int createdPlayers = 0;
        int updatedPlayers = 0;
        int photosUpdated = 0;

        for (Team team : teams) {
            List<Player> existingPlayers = playerRepository.findByTeamIdOrderByPositionAscShirtNumberAsc(team.getId());
            Map<String, Player> byName = new HashMap<>();
            for (Player player : existingPlayers) {
                byName.putIfAbsent(PlayerNameNormalizer.normalize(player.getName()), player);
            }

            List<PulseliveSquadProvider.SquadPlayer> squad = pulseliveSquadProvider.fetchSquad(team.getName());
            scannedPlayers += squad.size();

            for (PulseliveSquadProvider.SquadPlayer squadPlayer : squad) {
                String normalizedName = PlayerNameNormalizer.normalize(squadPlayer.displayName());
                Player player = byName.get(normalizedName);
                boolean created = false;
                if (player == null) {
                    player = new Player();
                    player.setTeamId(team.getId());
                    player.setName(squadPlayer.displayName());
                    byName.put(normalizedName, player);
                    created = true;
                }

                boolean changed = mergePlayer(player, squadPlayer);
                if (changed) {
                    playerRepository.save(player);
                    if (created) {
                        createdPlayers++;
                    } else {
                        updatedPlayers++;
                    }
                    if (hasText(player.getPhotoUrl())) {
                        photosUpdated++;
                    }
                }
            }
        }

        if (createdPlayers + updatedPlayers > 0) {
            clearCache("teamSquad");
            clearCache("playerDetail");
            clearCache("playerByApiId");
        }

        BackfillResult result = new BackfillResult(teams.size(), scannedPlayers, createdPlayers, updatedPlayers, photosUpdated);
        log.info("[PlayerSquadBackfillService] backfill result: {}", result);
        return result;
    }

    private boolean mergePlayer(Player player, PulseliveSquadProvider.SquadPlayer squadPlayer) {
        boolean changed = false;

        if (!hasText(player.getName()) && hasText(squadPlayer.displayName())) {
            player.setName(squadPlayer.displayName());
            changed = true;
        }
        if (!hasText(player.getChineseName())) {
            String chineseName = PlayerChineseNameMapper.map(squadPlayer.displayName());
            if (hasText(chineseName)) {
                player.setChineseName(chineseName);
                changed = true;
            }
        }
        if (!hasText(player.getPosition()) && hasText(squadPlayer.position())) {
            player.setPosition(squadPlayer.position());
            changed = true;
        }
        if (!hasText(player.getChinesePosition()) && hasText(squadPlayer.position())) {
            player.setChinesePosition(player.getPositionLabel());
            changed = true;
        }
        if (!hasText(player.getShirtNumber()) && hasText(squadPlayer.shirtNumber())) {
            player.setShirtNumber(squadPlayer.shirtNumber());
            changed = true;
        }
        if (!hasText(player.getNationality()) && hasText(squadPlayer.nationality())) {
            player.setNationality(squadPlayer.nationality());
            changed = true;
        }
        if (player.getDateOfBirth() == null && squadPlayer.dateOfBirth() != null) {
            player.setDateOfBirth(squadPlayer.dateOfBirth());
            changed = true;
        }
        if (player.getHeight() == null && squadPlayer.heightCm() != null) {
            player.setHeight(squadPlayer.heightCm());
            changed = true;
        }
        if (player.getWeight() == null && squadPlayer.weightKg() != null) {
            player.setWeight(squadPlayer.weightKg());
            changed = true;
        }
        if (!hasText(player.getFoot()) && hasText(squadPlayer.preferredFoot())) {
            player.setFoot(squadPlayer.preferredFoot());
            changed = true;
        }

        if (!hasText(player.getPhotoUrl()) && hasText(squadPlayer.officialId())) {
            String photoUrl = plPhotoProvider.findPhotoUrlByOfficialId(squadPlayer.officialId());
            if (hasText(photoUrl)) {
                player.setPhotoUrl(photoUrl);
                changed = true;
            }
        }

        return changed;
    }

    private void clearCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record BackfillResult(
            int scannedTeams,
            int scannedPlayers,
            int createdPlayers,
            int updatedPlayers,
            int photosUpdated
    ) {
    }
}
