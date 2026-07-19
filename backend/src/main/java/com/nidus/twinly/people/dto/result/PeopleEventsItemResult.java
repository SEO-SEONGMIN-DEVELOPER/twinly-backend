package com.nidus.twinly.people.dto.result;

import java.time.LocalDate;

public record PeopleEventsItemResult(
        LocalDate date,
        String relationshipChange,
        Integer intimacyDelta,
        String place,
        String preview
) {
}
