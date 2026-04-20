package com.premierleague.server.dto;

import com.premierleague.server.entity.AppUser;

public record AuthUserView(
        Long id,
        String nickName,
        String avatarUrl,
        boolean guest
) {
    public static AuthUserView from(AppUser user) {
        return new AuthUserView(
                user.getId(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                Boolean.TRUE.equals(user.getGuest())
        );
    }
}
