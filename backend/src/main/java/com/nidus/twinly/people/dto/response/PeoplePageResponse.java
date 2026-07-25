package com.nidus.twinly.people.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.people.dto.result.PeoplePageResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record PeoplePageResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(nullable = true)
        Long nextCursor,
        Boolean hasMore
) {

    public static PeoplePageResponse from(PeoplePageResult result) {
        return new PeoplePageResponse(result.nextCursor(), result.hasMore());
    }
}
