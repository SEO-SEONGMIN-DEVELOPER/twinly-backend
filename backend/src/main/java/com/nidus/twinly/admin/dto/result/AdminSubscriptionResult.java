package com.nidus.twinly.admin.dto.result;

import com.nidus.twinly.admin.domain.SubscriptionKind;

import java.time.Instant;

public record AdminSubscriptionResult(
        Long userId,
        SubscriptionKind kind,
        boolean active,
        Instant expiresAt,
        String store,
        Instant syncedAt
) {
}
