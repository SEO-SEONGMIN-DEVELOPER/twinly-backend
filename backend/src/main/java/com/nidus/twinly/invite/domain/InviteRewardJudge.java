package com.nidus.twinly.invite.domain;

import com.nidus.twinly.purchase.client.RevenueCatSubscription;
import com.nidus.twinly.purchase.client.RevenueCatSubscriptions;

import java.time.Instant;

public final class InviteRewardJudge {

    private InviteRewardJudge() {
    }

    public static RewardDecision judge(RevenueCatSubscriptions subscriptions, Instant now) {
        RevenueCatSubscription paid = subscriptions.latestPaid().orElse(null);

        if (paid == null || !paid.isAutoRenewing()) {
            return promotional(subscriptions, now);
        }
        if (paid.hasBillingIssue(now)) {
            return RewardDecision.waitFor(InviteRewardWaitingReason.BILLING_ISSUE);
        }
        if (!paid.isActive(now)) {
            return promotional(subscriptions, now);
        }
        if (paid.isTrial()) {
            return RewardDecision.waitFor(InviteRewardWaitingReason.STORE_TRIAL);
        }
        if (paid.isPlayStore()) {
            return RewardDecision.playDefer(paid.productId(), paid.expiresAt());
        }
        if (paid.isAppStore()) {
            return RewardDecision.appleOffer(paid.productId());
        }

        return promotional(subscriptions, now);
    }

    private static RewardDecision promotional(RevenueCatSubscriptions subscriptions, Instant now) {
        Instant entitlementExpiresAt = subscriptions.entitlementExpiresAt();

        return RewardDecision.promotional(entitlementExpiresAt != null && entitlementExpiresAt.isAfter(now) ? entitlementExpiresAt : now);
    }
}
