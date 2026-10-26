package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MeCompleteTwinUpdateResult;

import java.time.Instant;
import java.util.UUID;

public record MeCompleteTwinUpdateResponse(
        UUID updateId,
        Instant completedAt,
        Instant nextUpdatableAt
) {

    public static MeCompleteTwinUpdateResponse from(MeCompleteTwinUpdateResult result) {
        return new MeCompleteTwinUpdateResponse(
                result.updateId(),
                result.completedAt(),
                result.nextUpdatableAt()
        );
    }
}
