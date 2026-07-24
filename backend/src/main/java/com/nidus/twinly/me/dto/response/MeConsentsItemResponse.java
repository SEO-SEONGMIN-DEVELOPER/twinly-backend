package com.nidus.twinly.me.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.me.dto.result.MeConsentsItemResult;

import java.time.Instant;

public record MeConsentsItemResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long policyId,
        String title,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Integer version,
        String url,
        Boolean isRequired,
        Boolean isGranted,
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
