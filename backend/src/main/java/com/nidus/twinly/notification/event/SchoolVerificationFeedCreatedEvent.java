package com.nidus.twinly.notification.event;

import com.nidus.twinly.notification.entity.AppNotificationFeed;
import com.nidus.twinly.schoolverification.domain.SchoolVerificationStatus;

public record SchoolVerificationFeedCreatedEvent(
        AppNotificationFeed feed,
        SchoolVerificationStatus status
) {
}
