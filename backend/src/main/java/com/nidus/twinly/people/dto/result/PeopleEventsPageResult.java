package com.nidus.twinly.people.dto.result;

import java.time.LocalDate;

public record PeopleEventsPageResult(
        LocalDate nextCursor,
        Boolean hasMore
) {
}
