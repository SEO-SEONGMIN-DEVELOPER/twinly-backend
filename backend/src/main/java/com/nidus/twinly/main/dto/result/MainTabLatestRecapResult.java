package com.nidus.twinly.main.dto.result;

import java.time.Instant;

public record MainTabLatestRecapResult(
        Long seasonId,
        Instant createdAt
) {
}
