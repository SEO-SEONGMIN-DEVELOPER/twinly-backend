package com.nidus.twinly.people.dto.response;

import com.nidus.twinly.people.dto.result.PeopleEventsPageResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record PeopleEventsPageResponse(
        @Schema(nullable = true)
        LocalDate nextCursor,
        Boolean hasMore
) {

    public static PeopleEventsPageResponse from(PeopleEventsPageResult result) {
        return new PeopleEventsPageResponse(result.nextCursor(), result.hasMore());
    }
}
