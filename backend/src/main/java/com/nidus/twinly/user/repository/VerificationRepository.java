package com.nidus.twinly.user.repository;

import com.nidus.twinly.user.entity.Verification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationRepository extends JpaRepository<Verification, Long> {
}