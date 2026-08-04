package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MeConsentsItemResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record MeConsentsItemResponse(
        String policyId,
        String title,
        @Schema(nullable = true)
        String version,
        @Schema(nullable = true)
        String url,
        Boolean requiresAgreement,
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
                result.requiresAgreement(),
                result.isRequired(),
                result.isGranted(),
                result.grantedAt()
        );
    }
}
