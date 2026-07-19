package com.nidus.twinly.people.dto.response;

import com.nidus.twinly.people.dto.result.PeopleEventsItemResult;

import java.time.LocalDate;

public record PeopleEventsItemResponse(
        LocalDate date,
        String relationshipChange,
        Integer intimacyDelta,
        String place,
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
