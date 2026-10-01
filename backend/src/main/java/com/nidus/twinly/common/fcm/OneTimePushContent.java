package com.nidus.twinly.common.fcm;

import java.time.Instant;

public record OneTimePushContent(
        String title,
        String body,
        Instant createdAt
) {
}
