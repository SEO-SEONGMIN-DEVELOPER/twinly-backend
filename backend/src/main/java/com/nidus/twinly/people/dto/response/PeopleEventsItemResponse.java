package com.nidus.twinly.people.dto.response;

import com.nidus.twinly.people.dto.result.PeopleEventsItemResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record PeopleEventsItemResponse(
        LocalDate date,
        @Schema(nullable = true)
        String relationshipChange,
        @Schema(nullable = true)
        Integer intimacyDelta,
        String place,
        @Schema(nullable = true)
        String preview
) {

    public static PeopleEventsItemResponse from(PeopleEventsItemResult result) {
        return new PeopleEventsItemResponse(
                result.date(),
                result.relationshipChange(),
                result.intimacyDelta(),
                result.place(),
                result.preview()
        );
    }
}
