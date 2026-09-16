package com.nidus.twinly.auth.repository;

import com.nidus.twinly.auth.entity.IdentityVerificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdentityVerificationLogRepository extends JpaRepository<IdentityVerificationLog, Long> {

    Optional<IdentityVerificationLog> findFirstByTransactionIdOrderByIdDesc(String transactionId);
}
