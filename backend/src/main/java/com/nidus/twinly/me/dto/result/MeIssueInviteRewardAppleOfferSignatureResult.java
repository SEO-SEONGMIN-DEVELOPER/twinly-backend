package com.nidus.twinly.me.dto.result;

import java.util.UUID;

public record MeIssueInviteRewardAppleOfferSignatureResult(
        String productId,
        String offerId,
        String keyId,
        UUID nonce,
        long timestamp,
        UUID appAccountToken,
        String signature
) {
}
