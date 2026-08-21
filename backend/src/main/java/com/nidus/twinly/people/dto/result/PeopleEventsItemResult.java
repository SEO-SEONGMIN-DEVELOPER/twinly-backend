package com.nidus.twinly.people.dto.result;

import com.nidus.twinly.relationship.domain.RelationshipSpecificType;

import java.time.LocalDate;

public record PeopleEventsItemResult(
        LocalDate date,
        RelationshipSpecificType relationshipChange,
        Integer intimacyDelta,
        String place,
        String preview
) {
}
