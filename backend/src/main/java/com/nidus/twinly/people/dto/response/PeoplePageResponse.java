package com.nidus.twinly.people.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.people.dto.result.PeoplePageResult;

public record PeoplePageResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long nextCursor,
        Boolean hasMore
) {

    public static PeoplePageResponse from(PeoplePageResult result) {
        return new PeoplePageResponse(result.nextCursor(), result.hasMore());
    }
}
