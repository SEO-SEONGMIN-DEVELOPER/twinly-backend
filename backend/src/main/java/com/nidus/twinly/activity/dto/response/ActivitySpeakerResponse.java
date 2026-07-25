package com.nidus.twinly.activity.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.activity.dto.result.ActivitySpeakerResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record ActivitySpeakerResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        @Schema(nullable = true)
        String userName
) {

    public static ActivitySpeakerResponse from(ActivitySpeakerResult result) {
        return new ActivitySpeakerResponse(result.userId(), result.userName());
    }
}
