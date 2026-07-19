package com.nidus.twinly.people.dto.result;

import java.time.LocalDate;

public record PeopleIntimacySeriesItemResult(
        LocalDate date,
        Integer intimacy
) {
}
