package com.premierleague.server.controller;

import com.premierleague.server.dto.ApiResponse;
import com.premierleague.server.model.PlayerSocialProfile;
import com.premierleague.server.service.SocialService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 球员社媒 Controller - 对应字段字典 API 设计
 * GET /api/social/players
 */
@RestController
@RequestMapping("/api/social")
public class SocialController {
    
    private final SocialService socialService;
    
    public SocialController(SocialService socialService) {
        this.socialService = socialService;
    }
    
    /**
     * 获取球员社媒列表
     * GET /api/social/players?teamId=57&playerId=2004&platform=X
     */
    @GetMapping("/players")
    public ApiResponse<List<PlayerSocialProfile>> getPlayerSocialProfiles(
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Long playerId,
            @RequestParam(required = false) String platform
    ) {
        List<PlayerSocialProfile> result = socialService.getPlayerSocialProfiles(teamId, playerId, platform);
        return ApiResponse.ok(result);
    }
}
