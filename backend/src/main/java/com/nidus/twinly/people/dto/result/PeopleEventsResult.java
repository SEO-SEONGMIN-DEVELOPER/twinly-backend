package com.nidus.twinly.people.dto.result;

import java.util.List;

public record PeopleEventsResult(
        PeopleEventsPartnerResult partner,
        List<PeopleEventsItemResult> events,
        PeopleEventsPageResult page
) {
}
