package com.premierleague.server.controller;

import com.premierleague.server.dto.ApiResponse;
import com.premierleague.server.dto.FollowedTeamsView;
import com.premierleague.server.service.AuthService;
import com.premierleague.server.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/follows")
@RequiredArgsConstructor
public class FollowController {

    private final AuthService authService;
    private final FollowService followService;

    @GetMapping("/teams")
    public ResponseEntity<ApiResponse<FollowedTeamsView>> listFollowedTeams(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = AuthService.SESSION_HEADER, required = false) String sessionToken) {
        String token = authService.resolveToken(authorization, sessionToken);
        return authService.getAuthenticatedUser(token)
                .map(user -> ResponseEntity.ok(ApiResponse.ok(followService.listTeams(user))))
                .orElseGet(() -> ResponseEntity.status(401).body(ApiResponse.unauthorized("invalid session")));
    }

    @PostMapping("/teams/{teamId}")
    public ResponseEntity<ApiResponse<FollowedTeamsView>> followTeam(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = AuthService.SESSION_HEADER, required = false) String sessionToken,
            @PathVariable Long teamId) {
        String token = authService.resolveToken(authorization, sessionToken);
        return authService.getAuthenticatedUser(token)
                .map(user -> ResponseEntity.ok(ApiResponse.ok(followService.followTeam(user, teamId))))
                .orElseGet(() -> ResponseEntity.status(401).body(ApiResponse.unauthorized("invalid session")));
    }

    @DeleteMapping("/teams/{teamId}")
    public ResponseEntity<ApiResponse<FollowedTeamsView>> unfollowTeam(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = AuthService.SESSION_HEADER, required = false) String sessionToken,
            @PathVariable Long teamId) {
        String token = authService.resolveToken(authorization, sessionToken);
        return authService.getAuthenticatedUser(token)
                .map(user -> ResponseEntity.ok(ApiResponse.ok(followService.unfollowTeam(user, teamId))))
                .orElseGet(() -> ResponseEntity.status(401).body(ApiResponse.unauthorized("invalid session")));
    }
}
