package com.nidus.twinly.auth.repository;

import com.nidus.twinly.auth.entity.AnonSessionVerificationSession;
import com.nidus.twinly.common.domain.VerificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnonSessionVerificationSessionRepository extends JpaRepository<AnonSessionVerificationSession, Long> {

    Optional<AnonSessionVerificationSession> findByAnonSessionIdAndType(Long anonSessionId, VerificationType type);

    void deleteByAnonSessionId(Long anonSessionId);
}
