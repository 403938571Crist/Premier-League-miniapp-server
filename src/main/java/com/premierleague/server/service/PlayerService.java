package com.premierleague.server.service;

import com.premierleague.server.entity.Match;
import com.premierleague.server.entity.Player;
import com.premierleague.server.provider.FootballDataProvider;
import com.premierleague.server.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 球员服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final FootballDataProvider footballDataProvider;

    /**
     * 获取球员详情
     * GET /api/players/{id}
     * 
     * 先查数据库，没有则从 API 获取并保存
     */
    @Cacheable(value = "playerDetail", key = "#playerId")
    public Optional<Player> getPlayerById(Long playerId) {
        log.info("[PlayerService] Getting player: {}", playerId);
        
        // 1. 先查数据库
        Optional<Player> playerOpt = playerRepository.findById(playerId);
        if (playerOpt.isPresent()) {
            return playerOpt;
        }
        
        // 2. 从 API 获取
        Optional<Player> apiPlayer = footballDataProvider.fetchPlayer(playerId);
        if (apiPlayer.isPresent()) {
            // 保存到数据库
            Player player = apiPlayer.get();
            player = playerRepository.save(player);
            return Optional.of(player);
        }
        
        return Optional.empty();
    }

    /**
     * 根据 API ID 获取球员
     */
    @Cacheable(value = "playerByApiId", key = "#apiId")
    public Optional<Player> getPlayerByApiId(Long apiId) {
        log.info("[PlayerService] Getting player by apiId: {}", apiId);
        
        // 1. 先查数据库
        Optional<Player> playerOpt = playerRepository.findByApiId(apiId);
        if (playerOpt.isPresent()) {
            return playerOpt;
        }
        
        // 2. 从 API 获取
        Optional<Player> apiPlayer = footballDataProvider.fetchPlayer(apiId);
        if (apiPlayer.isPresent()) {
            Player player = apiPlayer.get();
            player = playerRepository.save(player);
            return Optional.of(player);
        }
        
        return Optional.empty();
    }

    /**
     * 获取球员最近比赛
     * GET /api/players/{id}/matches
     */
    @Cacheable(value = "playerMatches", key = "#playerId + '-' + #limit")
    public List<Match> getPlayerMatches(Long playerId, int limit) {
        log.info("[PlayerService] Getting matches for player: {}, limit: {}", playerId, limit);
        
        // 从 API 获取球员最近比赛
        return footballDataProvider.fetchPlayerMatches(playerId, limit);
    }

    /**
     * 获取球队阵容
     * 按位置分组返回
     */
    @Cacheable(value = "teamSquad", key = "#teamId")
    public List<Player> getTeamSquad(Long teamId) {
        log.info("[PlayerService] Getting squad for team: {}", teamId);
        
        // 1. 先查数据库
        List<Player> players = playerRepository.findByTeamIdOrderByPositionAscShirtNumberAsc(teamId);
        
        // 2. 如果数据库中没有，从 API 获取并保存
        if (players.isEmpty()) {
            List<Player> apiPlayers = footballDataProvider.fetchTeamSquad(teamId);
            if (!apiPlayers.isEmpty()) {
                // 批量保存
                players = playerRepository.saveAll(apiPlayers);
            }
        }
        
        return players;
    }

    /**
     * 搜索球员
     */
    @Cacheable(value = "playerSearch", key = "#keyword")
    public List<Player> searchPlayers(String keyword) {
        log.info("[PlayerService] Searching players with keyword: {}", keyword);
        return playerRepository.searchByName(keyword);
    }

    /**
     * 保存或更新球员
     */
    public Player savePlayer(Player player) {
        return playerRepository.save(player);
    }

    /**
     * 批量保存球员
     */
    public List<Player> saveAllPlayers(List<Player> players) {
        return playerRepository.saveAll(players);
    }

    /**
     * 获取身价最高的球员
     */
    @Cacheable(value = "teamMostValuablePlayers", key = "#teamId")
    public List<Player> getMostValuablePlayers(Long teamId) {
        return playerRepository.findMostValuablePlayersByTeamId(teamId);
    }
}
