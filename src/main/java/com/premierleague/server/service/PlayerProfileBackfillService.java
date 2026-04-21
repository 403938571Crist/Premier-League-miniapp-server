package com.premierleague.server.service;

import com.premierleague.server.entity.Player;
import com.premierleague.server.provider.PlPhotoProvider;
import com.premierleague.server.repository.PlayerRepository;
import com.premierleague.server.util.PlayerChineseNameMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerProfileBackfillService {

    private final PlayerRepository playerRepository;
    private final PlPhotoProvider plPhotoProvider;
    private final CacheManager cacheManager;

    @Transactional
    public BackfillResult backfillMissingProfiles(int limit) {
        return backfillMissingProfiles(limit, null);
    }

    @Transactional
    public BackfillResult backfillMissingProfiles(int limit, List<Long> teamIds) {
        int batchSize = Math.max(1, limit);
        List<Player> players = fetchTargetPlayers(teamIds, batchSize);
        boolean shouldFillChineseNameFallback = teamIds != null && !teamIds.isEmpty();

        int updatedPlayers = 0;
        int chineseNamesUpdated = 0;
        int photosUpdated = 0;

        for (Player player : players) {
            try {
                boolean changed = false;

                if (!hasText(player.getChineseName())) {
                    String chineseName = PlayerChineseNameMapper.map(player.getName());
                    if (hasText(chineseName)) {
                        player.setChineseName(chineseName);
                        chineseNamesUpdated++;
                        changed = true;
                    } else if (shouldFillChineseNameFallback) {
                        player.setChineseName(player.getName());
                        chineseNamesUpdated++;
                        changed = true;
                    }
                }

                if (!hasText(player.getPhotoUrl())) {
                    String photoUrl = plPhotoProvider.findUsablePhotoUrl(player.getName(), player.getPhotoUrl());
                    if (!hasText(photoUrl)) {
                        photoUrl = findExistingDuplicatePhoto(player);
                    }
                    if (hasText(photoUrl)) {
                        player.setPhotoUrl(photoUrl);
                        photosUpdated++;
                        changed = true;
                    }
                }

                if (changed) {
                    playerRepository.save(player);
                    updatedPlayers++;
                }
            } catch (Exception e) {
                log.warn("[PlayerProfileBackfillService] failed to backfill {}: {}", player.getName(), e.getMessage());
            }
        }

        if (updatedPlayers > 0) {
            clearCache("teamSquad");
            clearCache("playerDetail");
            clearCache("playerByApiId");
        }

        BackfillResult result = new BackfillResult(players.size(), updatedPlayers, chineseNamesUpdated, photosUpdated);
        log.info("[PlayerProfileBackfillService] backfill result: {}", result);
        return result;
    }

    private List<Player> fetchTargetPlayers(List<Long> teamIds, int batchSize) {
        if (teamIds == null || teamIds.isEmpty()) {
            return playerRepository.findPlayersNeedingProfileBackfill(PageRequest.of(0, batchSize));
        }
        return playerRepository.findPlayersNeedingProfileBackfillByTeamIds(teamIds, PageRequest.of(0, batchSize));
    }

    private void clearCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }

    private String findExistingDuplicatePhoto(Player player) {
        if (player.getTeamId() == null || player.getDateOfBirth() == null) {
            return null;
        }

        return playerRepository.findByTeamIdAndDateOfBirthOrderByIdAsc(player.getTeamId(), player.getDateOfBirth())
                .stream()
                .filter(candidate -> !player.getId().equals(candidate.getId()))
                .filter(candidate -> hasText(candidate.getPhotoUrl()))
                .filter(candidate -> isLikelySamePlayer(player.getName(), candidate.getName()))
                .map(Player::getPhotoUrl)
                .findFirst()
                .orElse(null);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isLikelySamePlayer(String left, String right) {
        if (!hasText(left) || !hasText(right)) {
            return false;
        }

        String normalizedLeft = normalizeName(left);
        String normalizedRight = normalizeName(right);
        return normalizedLeft.equals(normalizedRight)
                || normalizedLeft.contains(normalizedRight)
                || normalizedRight.contains(normalizedLeft);
    }

    private String normalizeName(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace('\u2019', '\'')
                .replace('\u2018', '\'')
                .replace('-', ' ');
        return normalized.replaceAll("[^a-z0-9' ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public record BackfillResult(
            int scanned,
            int updatedPlayers,
            int chineseNamesUpdated,
            int photosUpdated
    ) {
    }
}
