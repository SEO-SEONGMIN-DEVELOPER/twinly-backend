package com.nidus.twinly.purchase.client;

import java.time.Instant;

public record RevenueCatEntitlement(
        String entitlement,
        Instant expiresAt,
        String store,
        String periodType
) {

    public RevenueCatEntitlement(String entitlement, Instant expiresAt) {
        this(entitlement, expiresAt, null, null);
    }
}
