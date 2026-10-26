package com.nidus.twinly.me.dto.result;

public record MeAppNotificationsFeedsSeasonRecapTargetResult(
        String kind,
        Long seasonId
) implements MeAppNotificationsFeedsTargetResult {
}
