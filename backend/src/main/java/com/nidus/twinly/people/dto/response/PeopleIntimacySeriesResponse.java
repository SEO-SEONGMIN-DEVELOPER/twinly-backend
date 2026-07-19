package com.nidus.twinly.people.dto.response;

import com.nidus.twinly.people.dto.result.PeopleIntimacySeriesResult;

import java.util.List;

public record PeopleIntimacySeriesResponse(
        Integer currentIntimacy,
        List<PeopleIntimacySeriesItemResponse> intimacySeries
) {

    public static PeopleIntimacySeriesResponse from(PeopleIntimacySeriesResult result) {
        return new PeopleIntimacySeriesResponse(
                result.currentIntimacy(),
                result.intimacySeries().stream().map(PeopleIntimacySeriesItemResponse::from).toList()
        );
    }
}
