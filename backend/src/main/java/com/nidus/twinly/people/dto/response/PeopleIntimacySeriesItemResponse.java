package com.nidus.twinly.people.dto.response;

import com.nidus.twinly.people.dto.result.PeopleIntimacySeriesItemResult;

import java.time.LocalDate;

public record PeopleIntimacySeriesItemResponse(
        LocalDate date,
        Integer intimacy,
        Integer gameIntimacy
) {

    public static PeopleIntimacySeriesItemResponse from(PeopleIntimacySeriesItemResult result) {
        return new PeopleIntimacySeriesItemResponse(result.date(), result.intimacy(), result.gameIntimacy());
    }
}
