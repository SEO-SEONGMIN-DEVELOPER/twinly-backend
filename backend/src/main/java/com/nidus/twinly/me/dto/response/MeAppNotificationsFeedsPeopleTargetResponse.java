package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MeAppNotificationsFeedsPeopleTargetResult;

public record MeAppNotificationsFeedsPeopleTargetResponse(
        String kind
) implements MeAppNotificationsFeedsTargetResponse {

    public static MeAppNotificationsFeedsPeopleTargetResponse from(MeAppNotificationsFeedsPeopleTargetResult result) {
        return new MeAppNotificationsFeedsPeopleTargetResponse(result.kind());
    }
}
