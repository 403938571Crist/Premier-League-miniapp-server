package com.premierleague.server.controller;

import com.premierleague.server.dto.ApiResponse;
import com.premierleague.server.entity.Match;
import com.premierleague.server.entity.Player;
import com.premierleague.server.service.PlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 球员 Controller
 */
@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerService playerService;

    /**
     * 获取球员详情
     * GET /api/players/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<Player> getPlayerById(@PathVariable Long id) {
        return playerService.getPlayerById(id)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.notFound("player"));
    }

    /**
     * 获取球员最近比赛
     * GET /api/players/{id}/matches?limit=10
     */
    @GetMapping("/{id}/matches")
    public ApiResponse<List<Match>> getPlayerMatches(
            @PathVariable Long id,
            @RequestParam(defaultValue = "10") int limit) {
        List<Match> matches = playerService.getPlayerMatches(id, limit);
        return ApiResponse.ok(matches);
    }

    /**
     * 搜索球员
     * GET /api/players/search?keyword=xxx
     */
    @GetMapping("/search")
    public ApiResponse<List<Player>> searchPlayers(@RequestParam String keyword) {
        List<Player> players = playerService.searchPlayers(keyword);
        return ApiResponse.ok(players);
    }
}
