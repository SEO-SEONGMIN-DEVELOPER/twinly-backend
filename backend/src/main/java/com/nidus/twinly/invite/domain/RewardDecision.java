package com.nidus.twinly.invite.domain;

import java.time.Instant;

public record RewardDecision(
        Kind kind,
        String productId,
        Instant baseExpiresAt
) {

    public enum Kind {
        PROMOTIONAL,
        PLAY_DEFER,
        APPLE_OFFER,
        WAIT_STORE_TRIAL,
        WAIT_BILLING_ISSUE
    }

    public static RewardDecision promotional(Instant baseExpiresAt) {
        return new RewardDecision(Kind.PROMOTIONAL, null, baseExpiresAt);
    }

    public static RewardDecision playDefer(String productId, Instant baseExpiresAt) {
        return new RewardDecision(Kind.PLAY_DEFER, productId, baseExpiresAt);
    }

    public static RewardDecision appleOffer(String productId) {
        return new RewardDecision(Kind.APPLE_OFFER, productId, null);
    }

    public static RewardDecision waitFor(InviteRewardWaitingReason reason) {
        return new RewardDecision(reason == InviteRewardWaitingReason.STORE_TRIAL ? Kind.WAIT_STORE_TRIAL : Kind.WAIT_BILLING_ISSUE, null, null);
    }

    public boolean grantsNow() {
        return kind == Kind.PROMOTIONAL || kind == Kind.PLAY_DEFER;
    }

    public InviteRewardMethod method() {
        return switch (kind) {
            case PROMOTIONAL -> InviteRewardMethod.PROMOTIONAL;
            case PLAY_DEFER -> InviteRewardMethod.PLAY_DEFER;
            case APPLE_OFFER -> InviteRewardMethod.APPLE_OFFER;
            case WAIT_STORE_TRIAL, WAIT_BILLING_ISSUE -> null;
        };
    }

    public InviteRewardWaitingReason waitingReason() {
        return switch (kind) {
            case WAIT_STORE_TRIAL -> InviteRewardWaitingReason.STORE_TRIAL;
            case WAIT_BILLING_ISSUE -> InviteRewardWaitingReason.BILLING_ISSUE;
            case PROMOTIONAL, PLAY_DEFER, APPLE_OFFER -> null;
        };
    }

    public Instant targetExpiresAt() {
        return baseExpiresAt.plus(InvitePolicy.REWARD_DURATION);
    }
}
