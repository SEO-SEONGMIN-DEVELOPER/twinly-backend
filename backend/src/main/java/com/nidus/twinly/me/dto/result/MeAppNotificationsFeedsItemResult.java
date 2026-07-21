package com.nidus.twinly.me.dto.result;

import com.nidus.twinly.notification.domain.AppNotificationFeedType;

import java.time.Instant;

public record MeAppNotificationsFeedsItemResult(
        Long id,
        AppNotificationFeedType type,
        String title,
        String body,
        MeAppNotificationsFeedsTargetResult target,
        Boolean isRead,
        Instant createdAt
) {
}
