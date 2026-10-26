package com.nidus.twinly.me.dto.result;

import java.time.Instant;
import java.util.UUID;

public record MeCompleteTwinUpdateResult(
        UUID updateId,
        Instant completedAt,
        Instant nextUpdatableAt
) {
}
