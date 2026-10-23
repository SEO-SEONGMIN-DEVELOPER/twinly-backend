package com.nidus.twinly.common.fcm;

import java.time.Instant;

public record BalanceGameOpenedPushContent(
        String title,
        String body,
        Instant createdAt
) {
}
