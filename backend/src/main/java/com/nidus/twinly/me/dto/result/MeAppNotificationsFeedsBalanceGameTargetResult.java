package com.nidus.twinly.me.dto.result;

public record MeAppNotificationsFeedsBalanceGameTargetResult(
        String kind,
        Long userId
) implements MeAppNotificationsFeedsTargetResult {
}
