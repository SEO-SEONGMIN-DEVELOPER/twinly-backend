package com.nidus.twinly.me.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.me.dto.result.MeAppNotificationsFeedsSeasonRecapTargetResult;

public record MeAppNotificationsFeedsSeasonRecapTargetResponse(
        String kind,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long seasonId
) implements MeAppNotificationsFeedsTargetResponse {

    public static MeAppNotificationsFeedsSeasonRecapTargetResponse from(MeAppNotificationsFeedsSeasonRecapTargetResult result) {
        return new MeAppNotificationsFeedsSeasonRecapTargetResponse(
                result.kind(),
                result.seasonId()
        );
    }
}
