package com.nidus.twinly.people.dto.response;

import com.nidus.twinly.people.dto.result.PeopleEventBubbleLineResult;

public record PeopleEventBubbleLineResponse(
        String t,
        PeopleEventSpeakerResponse speaker,
        String action,
        String text
) implements PeopleEventLineResponse {

    public static PeopleEventBubbleLineResponse from(PeopleEventBubbleLineResult result) {
        return new PeopleEventBubbleLineResponse(
                result.t(),
                PeopleEventSpeakerResponse.from(result.speaker()),
                result.action(),
                result.text()
        );
    }
}
