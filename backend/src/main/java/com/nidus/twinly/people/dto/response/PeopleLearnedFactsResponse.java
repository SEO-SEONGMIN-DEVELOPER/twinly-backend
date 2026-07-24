package com.nidus.twinly.people.dto.response;

import com.nidus.twinly.people.dto.result.PeopleLearnedFactsResult;

public record PeopleLearnedFactsResponse(
        String learnedFacts
) {

    public static PeopleLearnedFactsResponse from(PeopleLearnedFactsResult result) {
        return new PeopleLearnedFactsResponse(result.learnedFacts());
    }
}
