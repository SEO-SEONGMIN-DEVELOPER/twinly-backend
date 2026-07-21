package com.nidus.twinly.me.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.me.dto.result.MeAppNotificationsFeedsChatTargetResult;

public record MeAppNotificationsFeedsChatTargetResponse(
        String kind,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long roomId
) implements MeAppNotificationsFeedsTargetResponse {

    public static MeAppNotificationsFeedsChatTargetResponse from(MeAppNotificationsFeedsChatTargetResult result) {
        return new MeAppNotificationsFeedsChatTargetResponse(
                result.kind(),
                result.roomId()
        );
    }
}
