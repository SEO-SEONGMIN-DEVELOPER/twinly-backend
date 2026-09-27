package com.nidus.twinly.purchase.service;

import com.nidus.twinly.auth.event.UserSignedUpEvent;
import com.nidus.twinly.common.logging.ErrorLog;
import com.nidus.twinly.common.logging.InfoLog;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.purchase.client.RevenueCatClient;
import com.nidus.twinly.purchase.entity.EarlySignupGrant;
import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.purchase.repository.EarlySignupGrantRepository;
import com.nidus.twinly.purchase.writer.EarlySignupGrantWriter;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Service
@RequiredArgsConstructor
public class EarlySignupGrantService {

    private final EarlySignupGrantRepository earlySignupGrantRepository;
    private final EarlySignupGrantWriter earlySignupGrantWriter;
    private final UserRepository userRepository;
    private final RevenueCatClient revenueCatClient;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserSignedUp(UserSignedUpEvent event) {
        earlySignupGrantRepository.findByUserIdAndGrantedAtIsNull(event.userId())
                .ifPresent(this::grantQuietly);
    }

    public void grantPending() {
        earlySignupGrantRepository.findAllByGrantedAtIsNullAndExpiresAtAfter(Instant.now())
                .forEach(this::grantQuietly);
    }

    private void grantQuietly(EarlySignupGrant grant) {
        try {
            User user = userRepository.findById(grant.getUserId()).orElse(null);
            if (user == null || user.getDeletedAt() != null) {
                return;
            }

            revenueCatClient.grantPromotional(user.getRevenueCatUserId().toString(), EntitlementReader.SIMULATION_ACCESS, grant.getExpiresAt());
            earlySignupGrantWriter.markGranted(grant.getId(), Instant.now());

            InfoLog.log(log, "선착순 가입 권한을 부여했습니다.", field("userId", grant.getUserId()), field("expiresAt", grant.getExpiresAt()));
        } catch (RuntimeException e) {
            ErrorLog.warn(log, ErrorCode.REVENUE_CAT_GRANT_FAILED.name(), String.valueOf(grant.getUserId()), e)
                    .log("선착순 가입 권한 부여 실패. 주기 작업이 다시 시도합니다.");
        }
    }
}
