package com.nidus.twinly.invite.domain;

import com.nidus.twinly.purchase.client.RevenueCatSubscription;
import com.nidus.twinly.purchase.client.RevenueCatSubscriptions;

import java.time.Duration;
import java.time.Instant;

public enum GrantCheck {
    APPLIED,
    NOT_APPLIED,
    UNKNOWN;

    private static final Duration TOLERANCE = Duration.ofMinutes(1);

    public static GrantCheck of(InviteRewardMethod method,
                                String productId,
                                Instant baseExpiresAt,
                                Instant targetExpiresAt,
                                RevenueCatSubscriptions subscriptions) {
        if (method == InviteRewardMethod.PROMOTIONAL) {
            return subscriptions.latestPromotionalExpiresAt()
                    .filter(expiresAt -> !expiresAt.isBefore(targetExpiresAt.minus(TOLERANCE)))
                    .isPresent() ? APPLIED : NOT_APPLIED;
        }

        RevenueCatSubscription subscription = subscriptions.subscription(productId).orElse(null);
        if (subscription == null) {
            return UNKNOWN;
        }

        boolean renewed = subscription.purchasedAt() != null && !subscription.purchasedAt().isBefore(baseExpiresAt.minus(TOLERANCE));
        if (renewed) {
            return UNKNOWN;
        }
        if (near(subscription.expiresAt(), targetExpiresAt)) {
            return APPLIED;
        }
        if (near(subscription.expiresAt(), baseExpiresAt)) {
            return NOT_APPLIED;
        }

        return UNKNOWN;
    }

    private static boolean near(Instant actual, Instant expected) {
        return Duration.between(actual, expected).abs().compareTo(TOLERANCE) <= 0;
    }
}
