package com.nidus.twinly.purchase.service;

import com.nidus.twinly.common.logging.ErrorLog;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.purchase.RevenueCatProperties;
import com.nidus.twinly.purchase.client.RevenueCatClient;
import com.nidus.twinly.purchase.client.RevenueCatEntitlement;
import com.nidus.twinly.purchase.dto.command.RevenueCatWebhookCommand;
import com.nidus.twinly.purchase.event.SimulationAccessGrantedEvent;
import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.purchase.writer.PurchaseWriter;
import com.nidus.twinly.season.writer.SeasonParticipationWriter;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
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
    private final EntitlementReader entitlementReader;
    private final SeasonParticipationWriter seasonParticipationWriter;
    private final ApplicationEventPublisher eventPublisher;

    public void receiveWebhook(RevenueCatWebhookCommand command) {
        log.info("RevenueCat webhook: type={}, environment={}, eventId={}", command.type(), command.environment(), command.eventId());

        if (!matchesEnvironment(command.environment())) {
            return;
        }

        command.appUserIds().forEach(this::sync);
    }

    @Async("purchaseSyncTaskExecutor")
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

        boolean hadAccess = entitlementReader.hasSimulationAccess(user.getId());
        purchaseWriter.replaceEntitlements(user.getId(), entitlements, syncedAt);
        boolean hasAccess = entitlementReader.hasSimulationAccess(user.getId());

        if (hasAccess) {
            seasonParticipationWriter.participateInCurrentSeason(user.getId());
        }

        if (!hadAccess && hasAccess) {
            eventPublisher.publishEvent(new SimulationAccessGrantedEvent(user.getId(), syncedAt));
        }
    }

    private boolean matchesEnvironment(String environment) {
        return environment == null || revenueCatProperties.environment().name().equalsIgnoreCase(environment);
    }

    private void sync(String appUserId) {
        findUser(appUserId).ifPresent(this::sync);
    }

    private Optional<User> findUser(String appUserId) {
        try {
            UUID revenueCatUserId = UUID.fromString(appUserId);
            return userRepository.findByRevenueCatUserId(revenueCatUserId);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
