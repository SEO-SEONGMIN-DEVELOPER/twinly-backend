package com.nidus.twinly.notification.event;

import java.time.Instant;

public record OneTimePushEvent(
        Long userId,
        String title,
        String body,
        Instant createdAt
) {
}
