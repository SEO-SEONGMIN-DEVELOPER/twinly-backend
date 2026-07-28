package com.nidus.twinly.people.dto.result;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PeopleEventSpeakerResult(
        @JsonProperty("user_id") Long userId,
        String userName
) {
}
