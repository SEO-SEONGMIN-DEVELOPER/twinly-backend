package com.nidus.twinly.me.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.me.dto.result.MeAppNotificationsFeedsItemResult;
import com.nidus.twinly.notification.domain.AppNotificationFeedType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record MeAppNotificationsFeedsItemResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long id,
        AppNotificationFeedType type,
        String title,
        String body,
        @Schema(nullable = true, description = "누르면 이동할 곳. 공지(notice)는 이동할 곳이 없어 null 이다.")
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
                result.target() != null ? MeAppNotificationsFeedsTargetResponse.from(result.target()) : null,
                result.isRead(),
                result.createdAt()
        );
    }
}
