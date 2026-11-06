package com.nidus.twinly.purchase.client;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public record RevenueCatSubscriptions(
        Instant entitlementExpiresAt,
        List<RevenueCatSubscription> subscriptions
) {

    public Optional<RevenueCatSubscription> latestPaid() {
        return subscriptions.stream()
                .filter(subscription -> !subscription.isPromotional())
                .max(Comparator.comparing(RevenueCatSubscription::expiresAt));
    }

    public Optional<RevenueCatSubscription> subscription(String productId) {
        return subscriptions.stream()
                .filter(subscription -> subscription.productId().equals(productId))
                .findFirst();
    }

    public Optional<Instant> latestPromotionalExpiresAt() {
        return subscriptions.stream()
                .filter(RevenueCatSubscription::isPromotional)
                .map(RevenueCatSubscription::expiresAt)
                .max(Comparator.naturalOrder());
    }
}
