package com.nidus.twinly.auth.repository;

import com.nidus.twinly.auth.entity.VerificationSession;
import com.nidus.twinly.common.domain.VerificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VerificationSessionRepository extends JpaRepository<VerificationSession, Long> {

    Optional<VerificationSession> findByTypeAndVerificationToken(VerificationType type, UUID verificationToken);

    Optional<VerificationSession> findByVerifiedToken(UUID verifiedToken);

    Optional<VerificationSession> findByTypeAndVerifiedToken(VerificationType type, UUID verifiedToken);
}