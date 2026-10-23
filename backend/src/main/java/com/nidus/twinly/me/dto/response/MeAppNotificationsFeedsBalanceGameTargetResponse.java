package com.nidus.twinly.me.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.me.dto.result.MeAppNotificationsFeedsBalanceGameTargetResult;

public record MeAppNotificationsFeedsBalanceGameTargetResponse(
        String kind,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId
) implements MeAppNotificationsFeedsTargetResponse {

    public static MeAppNotificationsFeedsBalanceGameTargetResponse from(MeAppNotificationsFeedsBalanceGameTargetResult result) {
        return new MeAppNotificationsFeedsBalanceGameTargetResponse(
                result.kind(),
                result.userId()
        );
    }
}
