package com.nidus.twinly.purchase.writer;

import com.nidus.twinly.purchase.client.RevenueCatEntitlement;
import com.nidus.twinly.purchase.entity.UserEntitlement;
import com.nidus.twinly.purchase.repository.UserEntitlementRepository;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PurchaseWriter {

    private final UserEntitlementRepository userEntitlementRepository;
    private final UserRepository userRepository;

    @Transactional
    public void markSyncAttempt(Long userId, Instant attemptedAt) {
        userRepository.markPurchasesSynced(userId, attemptedAt);
    }

    @Transactional
    public void replaceEntitlements(Long userId, List<RevenueCatEntitlement> entitlements, Instant syncedAt) {
        List<UserEntitlement> stored = userEntitlementRepository.findAllByUserId(userId);

        if (stored.stream().anyMatch(entitlement -> entitlement.getSyncedAt().isAfter(syncedAt))) {
            return;
        }

        Map<String, UserEntitlement> removed = stored.stream()
                .collect(Collectors.toMap(UserEntitlement::getEntitlement, Function.identity()));

        for (RevenueCatEntitlement entitlement : entitlements) {
            UserEntitlement existing = removed.remove(entitlement.entitlement());

            if (existing == null) {
                userEntitlementRepository.save(UserEntitlement.create(userId, entitlement.entitlement(), entitlement.expiresAt(), syncedAt));
            } else {
                existing.sync(entitlement.expiresAt(), syncedAt);
            }
        }

        userEntitlementRepository.deleteAll(removed.values());
    }
}
