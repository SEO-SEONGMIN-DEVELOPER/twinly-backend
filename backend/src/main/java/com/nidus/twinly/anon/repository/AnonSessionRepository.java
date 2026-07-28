package com.nidus.twinly.anon.repository;

import com.nidus.twinly.anon.entity.AnonSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AnonSessionRepository extends JpaRepository<AnonSession, Long> {

    Optional<AnonSession> findByToken(UUID token);

    boolean existsByNicknameAndIdNot(String nickname, Long id);
}