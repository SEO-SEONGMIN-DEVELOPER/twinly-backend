package com.nidus.twinly.people.dto.response;

import com.nidus.twinly.people.dto.result.PeopleIntimacyQuizStatusResult;

public record PeopleIntimacyQuizStatusResponse(
        Boolean myAnswered,
        Boolean partnerAnswered
) {

    public static PeopleIntimacyQuizStatusResponse from(PeopleIntimacyQuizStatusResult result) {
        return new PeopleIntimacyQuizStatusResponse(result.myAnswered(), result.partnerAnswered());
    }
}
