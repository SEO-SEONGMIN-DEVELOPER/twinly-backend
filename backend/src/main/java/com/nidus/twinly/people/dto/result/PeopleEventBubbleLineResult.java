package com.nidus.twinly.people.dto.result;

public record PeopleEventBubbleLineResult(
        String t,
        PeopleEventSpeakerResult speaker,
        String action,
        String text
) implements PeopleEventLineResult {
}
