package com.nidus.twinly.me.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.me.dto.result.MeAppNotificationsFeedsItemResult;
import com.nidus.twinly.notification.domain.AppNotificationFeedType;

import java.time.Instant;

public record MeAppNotificationsFeedsItemResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long id,
        AppNotificationFeedType type,
        String title,
        String body,
        MeAppNotificationsFeedsTargetResponse target,
        Boolean isRead,
        Instant createdAt
) {

    public static MeAppNotificationsFeedsItemResponse from(MeAppNotificationsFeedsItemResult result) {
        return new MeAppNotificationsFeedsItemResponse(
                result.id(),
                result.type(),
                result.title(),
                result.body(),
                MeAppNotificationsFeedsTargetResponse.from(result.target()),
                result.isRead(),
                result.createdAt()
        );
    }
}
