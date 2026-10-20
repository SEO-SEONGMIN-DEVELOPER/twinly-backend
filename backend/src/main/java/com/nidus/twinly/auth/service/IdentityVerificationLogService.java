package com.nidus.twinly.auth.service;

import com.nidus.twinly.auth.domain.IdentityVerificationResult;
import com.nidus.twinly.auth.entity.IdentityVerificationLog;
import com.nidus.twinly.auth.repository.IdentityVerificationLogRepository;
import com.nidus.twinly.common.logging.WarnLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityVerificationLogService {

    private final IdentityVerificationLogRepository identityVerificationLogRepository;

    @Transactional(readOnly = true)
    public boolean isRateLimited(Long anonSessionId, Instant now, Duration window, int limit) {
        return identityVerificationLogRepository
                .countByAnonSessionIdAndCreatedAtAfter(anonSessionId, now.minus(window)) >= limit;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void issued(Long anonSessionId, String requestNo, String transactionId) {
        identityVerificationLogRepository.save(IdentityVerificationLog.issue(anonSessionId, requestNo, transactionId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completed(Long anonSessionId, String requestNo, String transactionId,
                          IdentityVerificationResult result, String diHash) {
        IdentityVerificationLog verificationLog = identityVerificationLogRepository
                .findFirstByTransactionIdOrderByIdDesc(transactionId)
                .orElseGet(() -> {
                    WarnLog.log(log, "본인인증 발급 기록이 없어 결과 기록 시점에 새로 만듭니다.",
                            field("anonSessionId", anonSessionId), field("transactionId", transactionId));

                    return identityVerificationLogRepository.save(
                            IdentityVerificationLog.issue(anonSessionId, requestNo, transactionId));
                });

        verificationLog.complete(result, diHash);
    }
}
