package com.premierleague.server.service;

import com.premierleague.server.dto.AuthSessionRequest;
import com.premierleague.server.dto.AuthSessionView;
import com.premierleague.server.dto.AuthUserView;
import com.premierleague.server.dto.UpdateProfileRequest;
import com.premierleague.server.entity.AppUser;
import com.premierleague.server.entity.UserSession;
import com.premierleague.server.repository.AppUserRepository;
import com.premierleague.server.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    public static final String SESSION_HEADER = "X-Session-Token";
    private static final int SESSION_DAYS = 30;

    private final AppUserRepository appUserRepository;
    private final UserSessionRepository userSessionRepository;

    public AuthSessionView createAnonymousSession(AuthSessionRequest request) {
        AppUser user = appUserRepository.save(AppUser.builder()
                .displayName(normalizeName(request == null ? null : request.nickName()))
                .avatarUrl(normalizeUrl(request == null ? null : request.avatarUrl()))
                .guest(Boolean.TRUE)
                .authProvider("anonymous")
                .build());

        LocalDateTime now = LocalDateTime.now();
        UserSession session = userSessionRepository.save(UserSession.builder()
                .token(generateToken())
                .userId(user.getId())
                .deviceId(trimToNull(request == null ? null : request.deviceId()))
                .expiresAt(now.plusDays(SESSION_DAYS))
                .lastAccessedAt(now)
                .active(Boolean.TRUE)
                .build());

        return toSessionView(user, session);
    }

    public Optional<AuthSessionView> getSession(String rawToken) {
        return findValidSession(rawToken).map(tuple -> toSessionView(tuple.user(), tuple.session()));
    }

    public Optional<AppUser> getAuthenticatedUser(String rawToken) {
        return findValidSession(rawToken).map(SessionTuple::user);
    }

    public Optional<AuthUserView> updateProfile(String rawToken, UpdateProfileRequest request) {
        return getAuthenticatedUser(rawToken).map(user -> {
            if (request != null) {
                if (request.nickName() != null) {
                    user.setDisplayName(normalizeName(request.nickName()));
                }
                if (request.avatarUrl() != null) {
                    user.setAvatarUrl(normalizeUrl(request.avatarUrl()));
                }
            }
            AppUser saved = appUserRepository.save(user);
            return AuthUserView.from(saved);
        });
    }

    public boolean invalidateSession(String rawToken) {
        String token = extractToken(rawToken);
        if (token == null) {
            return false;
        }
        Optional<UserSession> sessionOptional = userSessionRepository.findByTokenAndActiveTrue(token);
        if (sessionOptional.isEmpty()) {
            return false;
        }
        UserSession session = sessionOptional.get();
        session.setActive(Boolean.FALSE);
        userSessionRepository.save(session);
        return true;
    }

    public String extractToken(String rawToken) {
        if (rawToken == null) {
            return null;
        }
        String trimmed = rawToken.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String bearer = trimmed.substring(7).trim();
            return bearer.isEmpty() ? null : bearer;
        }
        return trimmed;
    }

    public String resolveToken(String authorizationHeader, String sessionHeader) {
        String token = extractToken(authorizationHeader);
        return token != null ? token : extractToken(sessionHeader);
    }

    public HttpHeaders buildSessionHeaders(AuthSessionView sessionView) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + sessionView.token());
        headers.add(SESSION_HEADER, sessionView.token());
        return headers;
    }

    private Optional<SessionTuple> findValidSession(String rawToken) {
        String token = extractToken(rawToken);
        if (token == null) {
            return Optional.empty();
        }

        LocalDateTime now = LocalDateTime.now();
        userSessionRepository.deleteByExpiresAtBefore(now);
        return userSessionRepository.findByTokenAndActiveTrue(token)
                .filter(session -> session.getExpiresAt().isAfter(now))
                .flatMap(session -> appUserRepository.findById(session.getUserId()).map(user -> {
                    session.setLastAccessedAt(now);
                    userSessionRepository.save(session);
                    return new SessionTuple(user, session);
                }));
    }

    private AuthSessionView toSessionView(AppUser user, UserSession session) {
        return new AuthSessionView(session.getToken(), session.getExpiresAt(), AuthUserView.from(user));
    }

    private String generateToken() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private String normalizeName(String nickName) {
        String value = trimToNull(nickName);
        return value == null ? "Guest User" : value;
    }

    private String normalizeUrl(String avatarUrl) {
        return trimToNull(avatarUrl);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record SessionTuple(AppUser user, UserSession session) {
    }
}
