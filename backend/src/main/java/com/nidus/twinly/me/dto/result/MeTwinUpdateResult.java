package com.nidus.twinly.me.dto.result;

import com.nidus.twinly.twinupdate.domain.TwinUpdateStatus;

import java.time.Instant;
import java.util.UUID;

public record MeTwinUpdateResult(
        UUID updateId,
        TwinUpdateStatus status,
        Boolean isSurveyCompleted,
        Boolean isInterestsCompleted,
        Instant completedAt,
        Instant nextUpdatableAt
) {
}
