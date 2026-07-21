package com.nidus.twinly.me.dto.result;

public record MeAppNotificationsFeedsProfileTargetResult(
        String kind,
        Long userId
) implements MeAppNotificationsFeedsTargetResult {
}
