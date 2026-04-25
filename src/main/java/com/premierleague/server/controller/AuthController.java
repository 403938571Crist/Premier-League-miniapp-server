package com.premierleague.server.controller;

import com.premierleague.server.dto.ApiResponse;
import com.premierleague.server.dto.AuthSessionRequest;
import com.premierleague.server.dto.AuthSessionView;
import com.premierleague.server.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/session")
    public ResponseEntity<ApiResponse<AuthSessionView>> createSession(@Valid @RequestBody(required = false) AuthSessionRequest request) {
        AuthSessionView sessionView = authService.createAnonymousSession(request);
        HttpHeaders headers = authService.buildSessionHeaders(sessionView);
        return ResponseEntity.ok()
                .headers(headers)
                .body(ApiResponse.ok(sessionView));
    }

    @GetMapping("/session")
    public ResponseEntity<ApiResponse<AuthSessionView>> getSession(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = AuthService.SESSION_HEADER, required = false) String sessionToken) {
        String token = authService.resolveToken(authorization, sessionToken);
        return authService.getSession(token)
                .map(session -> ResponseEntity.ok()
                        .headers(authService.buildSessionHeaders(session))
                        .body(ApiResponse.ok(session)))
                .orElseGet(() -> ResponseEntity.status(401).body(ApiResponse.unauthorized("invalid session")));
    }

    @DeleteMapping("/session")
    public ResponseEntity<ApiResponse<Void>> deleteSession(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = AuthService.SESSION_HEADER, required = false) String sessionToken) {
        String token = authService.resolveToken(authorization, sessionToken);
        if (authService.invalidateSession(token)) {
            return ResponseEntity.ok(ApiResponse.ok());
        }
        return ResponseEntity.status(401).body(ApiResponse.unauthorized("invalid session"));
    }
}
