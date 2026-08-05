package com.nidus.twinly.common.fcm;

import com.nidus.twinly.notification.domain.AppNotificationFeedTargetType;
import com.nidus.twinly.notification.domain.AppNotificationFeedType;

import java.time.Instant;

public record FeedPushContent(
        Long appNotificationId,
        AppNotificationFeedType type,
        String title,
        String body,
        AppNotificationFeedTargetType targetKind,
        Long targetId,
        Instant createdAt
) {
}
