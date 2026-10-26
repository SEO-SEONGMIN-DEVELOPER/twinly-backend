package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MeTwinUpdateResult;
import com.nidus.twinly.twinupdate.domain.TwinUpdateStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record MeTwinUpdateResponse(
        UUID updateId,
        TwinUpdateStatus status,
        Boolean isSurveyCompleted,
        Boolean isInterestsCompleted,
        @Schema(nullable = true)
        Instant completedAt,
        @Schema(nullable = true)
        Instant nextUpdatableAt
) {

    public static MeTwinUpdateResponse from(MeTwinUpdateResult result) {
        return new MeTwinUpdateResponse(
                result.updateId(),
                result.status(),
                result.isSurveyCompleted(),
                result.isInterestsCompleted(),
                result.completedAt(),
                result.nextUpdatableAt()
        );
    }
}
