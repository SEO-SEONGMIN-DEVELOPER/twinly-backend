package com.nidus.twinly.purchase.client;

import java.time.Instant;

public record RevenueCatEntitlement(
        String entitlement,
        Instant expiresAt
) {
}
