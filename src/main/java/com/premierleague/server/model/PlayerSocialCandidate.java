package com.premierleague.server.model;

public record PlayerSocialCandidate(
        String platform,
        String handle,
        String profileUrl,
        String avatar,
        Boolean verified,
        String summary
) {
}
