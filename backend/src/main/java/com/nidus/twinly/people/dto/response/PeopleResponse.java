package com.nidus.twinly.people.dto.response;

import com.nidus.twinly.people.dto.result.PeopleResult;

import java.util.List;

public record PeopleResponse(
        List<PeopleItemResponse> people,
        PeopleThresholdResponse threshold,
        PeoplePageResponse page
) {

    public static PeopleResponse from(PeopleResult result) {
        return new PeopleResponse(
                result.people().stream().map(PeopleItemResponse::from).toList(),
                PeopleThresholdResponse.from(result.threshold()),
                PeoplePageResponse.from(result.page())
        );
    }
}
