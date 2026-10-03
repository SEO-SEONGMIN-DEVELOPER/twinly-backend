package com.nidus.twinly.common.fcm;

import java.time.Instant;

public record BalanceGameSummaryPushContent(
        Long roundId,
        Long matchedCount,
        String title,
        String body,
        Instant createdAt
) {
}
