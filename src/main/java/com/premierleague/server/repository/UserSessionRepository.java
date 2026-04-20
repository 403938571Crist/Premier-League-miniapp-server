package com.premierleague.server.repository;

import com.premierleague.server.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, String> {

    Optional<UserSession> findByTokenAndActiveTrue(String token);

    @Transactional
    void deleteByExpiresAtBefore(LocalDateTime cutoff);
}
