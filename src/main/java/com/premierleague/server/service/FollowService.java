package com.premierleague.server.service;

import com.premierleague.server.dto.FollowedTeamsView;
import com.premierleague.server.entity.AppUser;
import com.premierleague.server.entity.FollowedTeam;
import com.premierleague.server.repository.FollowedTeamRepository;
import com.premierleague.server.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowedTeamRepository followedTeamRepository;
    private final TeamRepository teamRepository;

    public FollowedTeamsView listTeams(AppUser user) {
        List<Long> teamIds = followedTeamRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(FollowedTeam::getTeamId)
                .collect(Collectors.toList());
        return new FollowedTeamsView(teamIds);
    }

    public FollowedTeamsView followTeam(AppUser user, Long teamId) {
        if (teamId == null || !teamRepository.existsById(teamId)) {
            throw new IllegalArgumentException("team not found");
        }
        if (!followedTeamRepository.existsByUserIdAndTeamId(user.getId(), teamId)) {
            followedTeamRepository.save(FollowedTeam.builder()
                    .userId(user.getId())
                    .teamId(teamId)
                    .build());
        }
        return listTeams(user);
    }

    public FollowedTeamsView unfollowTeam(AppUser user, Long teamId) {
        followedTeamRepository.deleteByUserIdAndTeamId(user.getId(), teamId);
        return listTeams(user);
    }
}
