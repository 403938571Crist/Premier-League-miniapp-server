package com.premierleague.server.dto;

import java.util.List;

public record FollowedTeamsView(
        List<Long> teamIds
) {
}
