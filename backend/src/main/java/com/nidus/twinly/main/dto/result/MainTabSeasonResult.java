package com.nidus.twinly.main.dto.result;

import java.time.Instant;

public record MainTabSeasonResult(
        Long seasonId,
        Instant serverNow,
        String progress
) {
}
