package com.nidus.twinly.people.dto.response;

import com.nidus.twinly.people.dto.result.PeopleEventNarrationLineResult;

public record PeopleEventNarrationLineResponse(
        String t,
        String text
) implements PeopleEventLineResponse {

    public static PeopleEventNarrationLineResponse from(PeopleEventNarrationLineResult result) {
        return new PeopleEventNarrationLineResponse(result.t(), result.text());
    }
}
