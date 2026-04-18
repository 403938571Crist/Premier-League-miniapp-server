package com.premierleague.server.model;

/**
 * 球员赛季数据 - 用于射手榜/助攻榜
 * 数据源：football-data.org /competitions/{id}/scorers
 */
public record PlayerStat(
        // 排名（由服务端排序后赋值）
        Integer rank,

        // 球员信息
        Long playerId,          // football-data apiId
        String playerName,
        String chineseName,     // DB 有映射时填充，否则为空
        String nationality,
        String position,
        String chinesePosition,
        Integer shirtNumber,

        // 球队信息
        Long teamId,
        String teamName,
        String teamShortName,
        String teamChineseName,
        String teamCrest,

        // 赛季数据
        Integer goals,
        Integer assists,
        Integer penalties,
        Integer playedMatches,

        // 球员头像 URL（来自 Premier League 官方 CDN）
        String photoUrl
) {
}
