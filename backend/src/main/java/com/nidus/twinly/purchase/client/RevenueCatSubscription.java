package com.nidus.twinly.purchase.client;

import java.time.Duration;
import java.time.Instant;

public record RevenueCatSubscription(
        String productId,
        String store,
        String periodType,
        Instant purchasedAt,
        Instant expiresAt,
        Instant unsubscribeDetectedAt,
        Instant billingIssuesDetectedAt,
        Instant gracePeriodExpiresAt
) {

    public static final String APP_STORE = "app_store";
    public static final String PLAY_STORE = "play_store";
    public static final String PROMOTIONAL = "promotional";

    private static final String TRIAL = "trial";
    private static final Duration BILLING_RETRY_WINDOW = Duration.ofDays(60);

    public boolean isPromotional() {
        return PROMOTIONAL.equals(store);
    }

    public boolean isAppStore() {
        return APP_STORE.equals(store);
    }

    public boolean isPlayStore() {
        return PLAY_STORE.equals(store);
    }

    public boolean isTrial() {
        return TRIAL.equals(periodType);
    }

    public boolean isAutoRenewing() {
        return unsubscribeDetectedAt == null;
    }

    public boolean isActive(Instant now) {
        return expiresAt.isAfter(now);
    }

    public boolean hasBillingIssue(Instant now) {
        if (gracePeriodExpiresAt != null && gracePeriodExpiresAt.isAfter(now)) {
            return true;
        }

        return billingIssuesDetectedAt != null && expiresAt.isAfter(now.minus(BILLING_RETRY_WINDOW));
    }
}
