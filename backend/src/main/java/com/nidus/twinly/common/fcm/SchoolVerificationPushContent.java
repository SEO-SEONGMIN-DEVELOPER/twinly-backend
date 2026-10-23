package com.nidus.twinly.common.fcm;

import com.nidus.twinly.schoolverification.domain.SchoolVerificationStatus;

import java.time.Instant;

public record SchoolVerificationPushContent(
        Long appNotificationId,
        SchoolVerificationStatus status,
        String title,
        String body,
        Instant createdAt
) {
}
