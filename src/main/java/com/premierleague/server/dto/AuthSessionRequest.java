package com.premierleague.server.dto;

public record AuthSessionRequest(
        String deviceId,
        String nickName,
        String avatarUrl
) {
}
