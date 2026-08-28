package com.nidus.twinly.purchase.service;

import com.nidus.twinly.purchase.RevenueCatProperties;
import com.nidus.twinly.purchase.client.RevenueCatClient;
import com.nidus.twinly.purchase.client.RevenueCatEntitlement;
import com.nidus.twinly.purchase.dto.command.RevenueCatWebhookCommand;
import com.nidus.twinly.purchase.writer.UserEntitlementWriter;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final RevenueCatProperties revenueCatProperties;
    private final RevenueCatClient revenueCatClient;
    private final UserRepository userRepository;
    private final UserEntitlementWriter userEntitlementWriter;

    public void receiveWebhook(RevenueCatWebhookCommand command) {
        log.info("RevenueCat webhook: type={}, environment={}, eventId={}", command.type(), command.environment(), command.eventId());

        if (!matchesEnvironment(command.environment())) {
            return;
        }

        command.appUserIds().forEach(this::sync);
    }

    private boolean matchesEnvironment(String environment) {
        return environment == null || revenueCatProperties.environment().name().equalsIgnoreCase(environment);
    }

    private void sync(String appUserId) {
        Long userId = findUserId(appUserId);

        if (userId == null) {
            return;
        }

        Instant syncedAt = Instant.now();
        List<RevenueCatEntitlement> entitlements = revenueCatClient.entitlements(appUserId);

        userEntitlementWriter.replaceAll(userId, entitlements, syncedAt);
    }

    private Long findUserId(String appUserId) {
        UUID revenueCatUserId;
        try {
            revenueCatUserId = UUID.fromString(appUserId);
        } catch (IllegalArgumentException e) {
            return null;
        }

        return userRepository.findByRevenueCatUserId(revenueCatUserId)
                .map(User::getId)
                .orElse(null);
    }
}
