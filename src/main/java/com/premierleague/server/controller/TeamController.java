package com.premierleague.server.controller;

import com.premierleague.server.dto.ApiResponse;
import com.premierleague.server.entity.Match;
import com.premierleague.server.entity.Player;
import com.premierleague.server.entity.Team;
import com.premierleague.server.service.MatchService;
import com.premierleague.server.service.PlayerService;
import com.premierleague.server.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 球队/积分榜 Controller
 */
@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {
    
    private final TeamService teamService;
    private final MatchService matchService;
    private final PlayerService playerService;
    
    /**
     * 获取积分榜
     * GET /api/teams/standings
     * GET /api/teams/standings?type=TOTAL
     * GET /api/teams/standings?type=HOME
     * GET /api/teams/standings?type=AWAY
     */
    @GetMapping("/standings")
    public ApiResponse<List<Team>> getStandings(
            @RequestParam(required = false, defaultValue = "TOTAL") String type) {
        List<Team> standings;
        
        if ("TOTAL".equals(type)) {
            standings = teamService.getStandings();
        } else {
            // HOME 和 AWAY 榜需要额外处理，目前返回总榜
            standings = teamService.getStandingsByType(type);
        }
        
        return ApiResponse.ok(standings);
    }
    
    /**
     * 获取所有球队
     * GET /api/teams
     */
    @GetMapping
    public ApiResponse<List<Team>> getAllTeams() {
        List<Team> teams = teamService.getAllTeams();
        return ApiResponse.ok(teams);
    }
    
    /**
     * 获取球队详情
     * GET /api/teams/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<Team> getTeamById(@PathVariable Long id) {
        return teamService.getTeamById(id)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.notFound("team"));
    }
    
    /**
     * 获取球队阵容
     * GET /api/teams/{id}/squad
     */
    @GetMapping("/{id}/squad")
    public ApiResponse<Map<String, Object>> getTeamSquad(@PathVariable Long id) {
        Map<String, Object> squad = teamService.getTeamSquad(id);
        return ApiResponse.ok(squad);
    }
    
    /**
     * 获取球队赛程
     * GET /api/teams/{id}/matches
     */
    @GetMapping("/{id}/matches")
    public ApiResponse<List<Match>> getTeamMatches(@PathVariable Long id) {
        List<Match> matches = matchService.getMatchesByTeam(id);
        return ApiResponse.ok(matches);
    }
    
    /**
     * 获取球队统计
     * GET /api/teams/{id}/stats
     */
    @GetMapping("/{id}/stats")
    public ApiResponse<Map<String, Object>> getTeamStats(@PathVariable Long id) {
        Map<String, Object> stats = teamService.getTeamStats(id);
        return ApiResponse.ok(stats);
    }
}
