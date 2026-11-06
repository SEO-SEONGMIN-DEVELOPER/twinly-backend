package com.nidus.twinly.invite.client;

import java.time.Instant;

public record AppStoreTransaction(
        String productId,
        Integer offerType,
        String offerIdentifier,
        Instant purchasedAt,
        Instant expiresAt
) {

    private static final int PROMOTIONAL_OFFER = 2;

    public boolean appliedOffer(String offerId) {
        return offerType != null && offerType == PROMOTIONAL_OFFER && offerId.equals(offerIdentifier);
    }
}
