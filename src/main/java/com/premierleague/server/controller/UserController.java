package com.premierleague.server.controller;

import com.premierleague.server.dto.ApiResponse;
import com.premierleague.server.dto.AuthUserView;
import com.premierleague.server.dto.UpdateProfileRequest;
import com.premierleague.server.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthUserView>> getCurrentUser(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = AuthService.SESSION_HEADER, required = false) String sessionToken) {
        String token = authService.resolveToken(authorization, sessionToken);
        return authService.getAuthenticatedUser(token)
                .map(user -> ResponseEntity.ok(ApiResponse.ok(AuthUserView.from(user))))
                .orElseGet(() -> ResponseEntity.status(401).body(ApiResponse.unauthorized("invalid session")));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<AuthUserView>> updateCurrentUser(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = AuthService.SESSION_HEADER, required = false) String sessionToken,
            @Valid @RequestBody(required = false) UpdateProfileRequest request) {
        String token = authService.resolveToken(authorization, sessionToken);
        return authService.updateProfile(token, request)
                .map(view -> ResponseEntity.ok(ApiResponse.ok(view)))
                .orElseGet(() -> ResponseEntity.status(401).body(ApiResponse.unauthorized("invalid session")));
    }
}
