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
        // 中文名：缺失或还是英文占位（等于 name/displayName、或整体不含 CJK 字符）时，用 mapper 覆盖
        String mappedChinese = PlayerChineseNameMapper.map(squadPlayer.displayName());
        if (hasText(mappedChinese)
                && (!hasText(player.getChineseName())
                        || isEnglishChineseNamePlaceholder(player.getChineseName(), player.getName(), squadPlayer.displayName()))) {
            if (!mappedChinese.equals(player.getChineseName())) {
                player.setChineseName(mappedChinese);
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

        // 近期冬窗/夏窗转会的球员：PL CDN p{id}.png 还在回旧俱乐部球衣照片，
        // 每次 squad sync 都强制重新解析到 Wikipedia（或显式 override）。
        if (plPhotoProvider.isForceWikipediaRefresh(player.getName())) {
            String fresh = plPhotoProvider.findForceRefreshPhoto(player.getName());
            if (hasText(fresh) && !fresh.equals(player.getPhotoUrl())) {
                log.info("[PlayerSquadBackfillService] force-refresh photo for {}: {} -> {}",
                        player.getName(), player.getPhotoUrl(), fresh);
                player.setPhotoUrl(fresh);
                changed = true;
            }
        } else if (!hasText(player.getPhotoUrl()) && hasText(squadPlayer.officialId())) {
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

    private static boolean containsCjk(String value) {
        if (value == null || value.isEmpty()) return false;
        for (int i = 0; i < value.length(); i++) {
            if (Character.UnicodeBlock.of(value.charAt(i))
                    == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEnglishChineseNamePlaceholder(String chineseName, String... englishVariants) {
        if (chineseName == null || chineseName.isBlank()) return true;
        if (containsCjk(chineseName)) return false;
        String trimmed = chineseName.trim();
        for (String variant : englishVariants) {
            if (variant != null && !variant.isBlank() && trimmed.equalsIgnoreCase(variant.trim())) {
                return true;
            }
        }
        // 完全无 CJK 的任何值都当占位看（保底）
        return true;
    }

    public record BackfillResult(
            int scannedTeams,
            int scannedPlayers,
            int createdPlayers,
            int updatedPlayers,
            int photosUpdated
    ) {
    }

    /**
     * 按 PlPhotoProvider.FORCE_WIKIPEDIA_REFRESH 名单逐人扫 DB，强制刷新 photo_url。
     *
     * 用途：squad sync 只会处理「Pulselive 当前赛季阵容里有的人」。被租借到非英超俱乐部的球员
     * (例：Rashford 去巴萨、Højlund 去那不勒斯) 在 Pulselive Man Utd squad 里已经不存在，
     * squad 路径摸不到，photo_url 永久卡在旧的 PL 传统 CDN URL。这个方法不走 squad，直接按名字匹配 DB 行。
     *
     * 返回：scanned / updated 统计。
     */
    @Transactional
    public TransferPhotoRefreshResult refreshTransferPhotos() {
        int scanned = 0;
        int updated = 0;
        java.util.List<String> updatedNames = new java.util.ArrayList<>();

        for (String name : plPhotoProvider.getForceRefreshPlayerNames()) {
            java.util.List<Player> matches = playerRepository.findByName(name);
            scanned += matches.size();
            if (matches.isEmpty()) {
                log.debug("[PlayerSquadBackfillService] refresh-transfer-photos: no row for '{}'", name);
                continue;
            }
            String fresh = plPhotoProvider.findForceRefreshPhoto(name);
            if (!hasText(fresh)) {
                log.warn("[PlayerSquadBackfillService] refresh-transfer-photos: cannot resolve fresh URL for '{}'", name);
                continue;
            }
            for (Player p : matches) {
                if (!fresh.equals(p.getPhotoUrl())) {
                    log.info("[PlayerSquadBackfillService] refresh-transfer-photos '{}' (id={}, team_id={}): {} -> {}",
                            p.getName(), p.getId(), p.getTeamId(), p.getPhotoUrl(), fresh);
                    p.setPhotoUrl(fresh);
                    playerRepository.save(p);
                    updated++;
                    updatedNames.add(p.getName() + "#" + p.getId());
                }
            }
        }

        if (updated > 0) {
            clearCache("teamSquad");
            clearCache("playerDetail");
            clearCache("playerByApiId");
        }

        return new TransferPhotoRefreshResult(plPhotoProvider.getForceRefreshPlayerNames().size(), scanned, updated, updatedNames);
    }

    public record TransferPhotoRefreshResult(
            int watchlistSize,
            int scannedRows,
            int updatedRows,
            java.util.List<String> updatedIdentifiers
    ) {
    }
}
