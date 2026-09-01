package com.nidus.twinly.auth.repository;

import com.nidus.twinly.auth.entity.AnonSessionIdentityVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnonSessionIdentityVerificationRepository extends JpaRepository<AnonSessionIdentityVerification, Long> {

    Optional<AnonSessionIdentityVerification> findByAnonSessionId(Long anonSessionId);

    void deleteByAnonSessionId(Long anonSessionId);
}
