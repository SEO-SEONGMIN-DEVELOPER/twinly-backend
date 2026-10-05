package com.nidus.twinly.people.dto.response;

import com.nidus.twinly.people.dto.result.PeopleIntimacyQuizMatchRateResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record PeopleIntimacyQuizMatchRateResponse(
        Integer comparedCount,
        Integer matchedCount,
        @Schema(nullable = true)
        Double matchRate
) {

    public static PeopleIntimacyQuizMatchRateResponse from(PeopleIntimacyQuizMatchRateResult result) {
        return new PeopleIntimacyQuizMatchRateResponse(
                result.comparedCount(),
                result.matchedCount(),
                result.matchRate()
        );
    }
}
