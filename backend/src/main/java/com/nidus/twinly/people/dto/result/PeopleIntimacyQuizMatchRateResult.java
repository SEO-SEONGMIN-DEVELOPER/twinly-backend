package com.nidus.twinly.people.dto.result;

public record PeopleIntimacyQuizMatchRateResult(
        Integer comparedCount,
        Integer matchedCount,
        Double matchRate
) {
}
