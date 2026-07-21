package com.nidus.twinly.me.dto.result;

public record MeAppNotificationsFeedsChatTargetResult(
        String kind,
        Long roomId
) implements MeAppNotificationsFeedsTargetResult {
}
