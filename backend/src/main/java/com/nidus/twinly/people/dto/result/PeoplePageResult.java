package com.nidus.twinly.people.dto.result;

public record PeoplePageResult(
        Long nextCursor,
        Boolean hasMore
) {
}
