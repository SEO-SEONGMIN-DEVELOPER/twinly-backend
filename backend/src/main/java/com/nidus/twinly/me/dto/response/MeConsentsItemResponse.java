package com.nidus.twinly.me.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.me.dto.result.MeConsentsItemResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record MeConsentsItemResponse(
        String policyId,
        String title,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(nullable = true)
        Integer version,
        @Schema(nullable = true)
        String url,
        @Schema(nullable = true)
        Boolean isRequired,
        Boolean isGranted,
        @Schema(nullable = true)
        Instant grantedAt
) {

    public static MeConsentsItemResponse from(MeConsentsItemResult result) {
        return new MeConsentsItemResponse(
                result.policyId(),
                result.title(),
                result.version(),
                result.url(),
                result.isRequired(),
                result.isGranted(),
                result.grantedAt()
        );
    }
}
