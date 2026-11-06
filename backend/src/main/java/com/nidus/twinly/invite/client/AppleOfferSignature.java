package com.nidus.twinly.invite.client;

import java.util.UUID;

public record AppleOfferSignature(
        String productId,
        String offerId,
        String keyId,
        UUID nonce,
        long timestamp,
        UUID appAccountToken,
        String signature
) {
}
