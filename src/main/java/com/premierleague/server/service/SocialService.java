package com.premierleague.server.service;

import com.premierleague.server.entity.PlayerSocial;
import com.premierleague.server.model.PlayerSocialProfile;
import com.premierleague.server.repository.PlayerSocialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 球员社媒服务 - 支持 MySQL + Caffeine 本地缓存
 */
@Slf4j
@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = "social")
public class SocialService {
    
    private final PlayerSocialRepository playerSocialRepository;
    
    /**
     * 获取球员社媒列表 - GET /api/social/players
     * 缓存6小时
     */
    @Cacheable(value = "socialPlayers", key = "#teamId+'-'+#playerId+'-'+#platform")
    public List<PlayerSocialProfile> getPlayerSocialProfiles(Long teamId, Long playerId, String platform) {
        log.debug("Fetching social profiles from database: teamId={}, playerId={}, platform={}", 
                teamId, playerId, platform);
        
        List<PlayerSocial> profiles;
        
        // 根据条件查询
        if (teamId != null && platform != null) {
            profiles = playerSocialRepository.findByTeamIdAndPlatform(teamId, platform);
        } else if (teamId != null) {
            profiles = playerSocialRepository.findByTeamId(teamId);
        } else if (playerId != null) {
            profiles = playerSocialRepository.findByPlayerId(playerId);
        } else if (platform != null) {
            profiles = playerSocialRepository.findByPlatform(platform);
        } else {
            profiles = playerSocialRepository.findAll();
        }
        
        return profiles.stream()
                .map(this::convertToProfile)
                .collect(Collectors.toList());
    }
    
    /**
     * 将 Entity 转为 DTO
     */
    private PlayerSocialProfile convertToProfile(PlayerSocial entity) {
        return new PlayerSocialProfile(
                entity.getId(),
                entity.getPlayerId(),
                entity.getPlayerName(),
                entity.getTeamId(),
                entity.getTeamName(),
                entity.getPlatform(),
                entity.getHandle(),
                entity.getProfileUrl(),
                entity.getAvatar(),
                entity.getVerified(),
                entity.getSummary(),
                entity.getLastActiveAt() != null ? entity.getLastActiveAt().toString() : null
        );
    }
}
