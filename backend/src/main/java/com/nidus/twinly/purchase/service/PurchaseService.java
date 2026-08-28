package com.nidus.twinly.purchase.service;

import com.nidus.twinly.common.logging.ErrorLog;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.purchase.RevenueCatProperties;
import com.nidus.twinly.purchase.client.RevenueCatClient;
import com.nidus.twinly.purchase.client.RevenueCatEntitlement;
import com.nidus.twinly.purchase.dto.command.RevenueCatWebhookCommand;
import com.nidus.twinly.purchase.writer.PurchaseWriter;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseService {

    private static final Duration SYNC_INTERVAL = Duration.ofSeconds(30);

    private final RevenueCatProperties revenueCatProperties;
    private final RevenueCatClient revenueCatClient;
    private final UserRepository userRepository;
    private final PurchaseWriter purchaseWriter;

    public void receiveWebhook(RevenueCatWebhookCommand command) {
        log.info("RevenueCat webhook: type={}, environment={}, eventId={}", command.type(), command.environment(), command.eventId());

        if (!matchesEnvironment(command.environment())) {
            return;
        }

        command.appUserIds().forEach(this::sync);
    }

    public void syncIfStale(User user) {
        Instant now = Instant.now();

        if (!user.needsPurchasesSync(now.minus(SYNC_INTERVAL))) {
            return;
        }

        purchaseWriter.markSyncAttempt(user.getId(), now);

        try {
            sync(user);
        } catch (RuntimeException e) {
            ErrorLog.warn(log, ErrorCode.REVENUE_CAT_SYNC_FAILED.name(), String.valueOf(user.getId()), e)
                    .log("RevenueCat 동기화 실패. 저장된 구매 상태를 유지합니다.");
        }
    }

    public void sync(User user) {
        Instant syncedAt = Instant.now();
        List<RevenueCatEntitlement> entitlements = revenueCatClient.entitlements(user.getRevenueCatUserId().toString());

        purchaseWriter.replaceEntitlements(user.getId(), entitlements, syncedAt);
    }

    private boolean matchesEnvironment(String environment) {
        return environment == null || revenueCatProperties.environment().name().equalsIgnoreCase(environment);
    }

    private void sync(String appUserId) {
        findUser(appUserId).ifPresent(this::sync);
    }

    private Optional<User> findUser(String appUserId) {
        UUID revenueCatUserId;
        try {
            revenueCatUserId = UUID.fromString(appUserId);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        return userRepository.findByRevenueCatUserId(revenueCatUserId);
    }
}
