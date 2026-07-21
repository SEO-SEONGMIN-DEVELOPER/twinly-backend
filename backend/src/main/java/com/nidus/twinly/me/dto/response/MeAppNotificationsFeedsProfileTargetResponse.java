package com.nidus.twinly.me.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.me.dto.result.MeAppNotificationsFeedsProfileTargetResult;

public record MeAppNotificationsFeedsProfileTargetResponse(
        String kind,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId
) implements MeAppNotificationsFeedsTargetResponse {

    public static MeAppNotificationsFeedsProfileTargetResponse from(MeAppNotificationsFeedsProfileTargetResult result) {
        return new MeAppNotificationsFeedsProfileTargetResponse(
                result.kind(),
                result.userId()
        );
    }
}
