package com.nidus.twinly.people.dto.result;

import java.util.List;

public record PeopleIntimacySeriesResult(
        Integer currentIntimacy,
        List<PeopleIntimacySeriesItemResult> intimacySeries
) {
}
