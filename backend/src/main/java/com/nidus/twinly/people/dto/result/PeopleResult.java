package com.nidus.twinly.people.dto.result;

import java.util.List;

public record PeopleResult(
        List<PeopleItemResult> people,
        PeopleThresholdResult threshold,
        PeoplePageResult page
) {
}
