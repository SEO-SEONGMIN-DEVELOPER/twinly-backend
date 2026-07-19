package com.nidus.twinly.people.dto.response;

import com.nidus.twinly.people.dto.result.PeopleEventsResult;

import java.util.List;

public record PeopleEventsResponse(
        PeopleEventsPartnerResponse partner,
        List<PeopleEventsItemResponse> events,
        PeopleEventsPageResponse page
) {

    public static PeopleEventsResponse from(PeopleEventsResult result) {
        return new PeopleEventsResponse(
                PeopleEventsPartnerResponse.from(result.partner()),
                result.events().stream().map(PeopleEventsItemResponse::from).toList(),
                PeopleEventsPageResponse.from(result.page())
        );
    }
}
