package com.nidus.twinly.activity.dto.result;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ActivitySpeakerResult(
        @JsonProperty("user_id") Long userId,
        String userName
) {
}
