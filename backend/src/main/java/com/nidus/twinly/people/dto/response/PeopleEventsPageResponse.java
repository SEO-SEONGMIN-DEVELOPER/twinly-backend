package com.nidus.twinly.people.dto.response;

import com.nidus.twinly.people.dto.result.PeopleEventsPageResult;

import java.time.LocalDate;

public record PeopleEventsPageResponse(
        LocalDate nextCursor,
        Boolean hasMore
) {

    public static PeopleEventsPageResponse from(PeopleEventsPageResult result) {
        return new PeopleEventsPageResponse(result.nextCursor(), result.hasMore());
    }
}
