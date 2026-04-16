package com.premierleague.server.controller;

import com.premierleague.server.dto.ApiResponse;
import com.premierleague.server.dto.PageResult;
import com.premierleague.server.entity.Match;
import com.premierleague.server.service.MatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * 赛程/比赛 Controller
 */
@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class MatchController {
    
    private final MatchService matchService;
    
    /**
     * 获取今日比赛
     * GET /api/matches/today
     */
    @GetMapping("/today")
    public ApiResponse<List<Match>> getTodayMatches() {
        List<Match> matches = matchService.getTodayMatches();
        return ApiResponse.ok(matches);
    }
    
    /**
     * 获取进行中的比赛
     * GET /api/matches/live
     */
    @GetMapping("/live")
    public ApiResponse<List<Match>> getLiveMatches() {
        List<Match> matches = matchService.getLiveMatches();
        return ApiResponse.ok(matches);
    }
    
    /**
     * 获取比赛列表
     * 支持按日期或轮次查询
     * GET /api/matches?date=2026-04-12
     * GET /api/matches?matchday=32
     */
    @GetMapping
    public ApiResponse<List<Match>> getMatches(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer matchday) {
        
        List<Match> matches;
        
        if (date != null) {
            // 按日期查询
            matches = matchService.getMatchesByDate(date);
        } else if (matchday != null) {
            // 按轮次查询
            matches = matchService.getMatchesByMatchday(matchday);
        } else {
            // 默认返回当前轮次
            matches = matchService.getCurrentMatchday();
        }
        
        return ApiResponse.ok(matches);
    }
    
    /**
     * 获取当前轮次
     * GET /api/matches/current
     */
    @GetMapping("/current")
    public ApiResponse<List<Match>> getCurrentMatchday() {
        List<Match> matches = matchService.getCurrentMatchday();
        return ApiResponse.ok(matches);
    }
    
    /**
     * 获取比赛详情
     * GET /api/matches/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<Match> getMatchById(@PathVariable Long id) {
        Match match = matchService.getMatchById(id);
        if (match == null) {
            return ApiResponse.notFound("match");
        }
        return ApiResponse.ok(match);
    }
    
    /**
     * 获取两队的交锋记录
     * GET /api/matches/headtohead?team1=1&team2=2
     */
    @GetMapping("/headtohead")
    public ApiResponse<List<Match>> getHeadToHead(
            @RequestParam Long team1,
            @RequestParam Long team2) {
        List<Match> matches = matchService.getHeadToHead(team1, team2);
        return ApiResponse.ok(matches);
    }
    
    /**
     * 获取某日期范围的比赛
     * GET /api/matches/range?start=2026-04-12&end=2026-04-19
     */
    @GetMapping("/range")
    public ApiResponse<List<Match>> getMatchesByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        
        LocalDateTime startTime = start.atStartOfDay();
        LocalDateTime endTime = end.atTime(LocalTime.MAX);
        
        List<Match> matches = matchService.getMatchesByDateRange(startTime, endTime);
        return ApiResponse.ok(matches);
    }
    
    /**
     * 获取赛程日历
     * GET /api/matches/calendar
     */
    @GetMapping("/calendar")
    public ApiResponse<Map<String, List<Match>>> getMatchesCalendar() {
        Map<String, List<Match>> calendar = matchService.getMatchesCalendar();
        return ApiResponse.ok(calendar);
    }
}
