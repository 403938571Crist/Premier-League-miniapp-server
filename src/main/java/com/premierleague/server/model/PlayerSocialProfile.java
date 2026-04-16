package com.premierleague.server.model;

/**
 * 球员社媒主页 - 对应字段字典 6. 球员社媒字段
 * 用于 GET /api/social/players
 */
public record PlayerSocialProfile(
        String id,
        Long playerId,
        String playerName,
        Long teamId,
        String teamName,
        String platform,
        String handle,
        String profileUrl,
        String avatar,
        Boolean verified,
        String summary,
        String lastActiveAt
) {
}
