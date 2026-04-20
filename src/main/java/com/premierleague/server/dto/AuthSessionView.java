package com.premierleague.server.dto;

import java.time.LocalDateTime;

public record AuthSessionView(
        String token,
        LocalDateTime expiresAt,
        AuthUserView user
) {
}
