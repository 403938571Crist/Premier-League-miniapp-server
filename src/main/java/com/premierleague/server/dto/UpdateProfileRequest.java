package com.premierleague.server.dto;

public record UpdateProfileRequest(
        String nickName,
        String avatarUrl
) {
}
