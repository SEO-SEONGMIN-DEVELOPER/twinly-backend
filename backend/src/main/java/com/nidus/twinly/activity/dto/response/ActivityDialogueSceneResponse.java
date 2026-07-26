package com.nidus.twinly.activity.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.activity.dto.result.ActivityDialogueSceneResult;

import java.time.OffsetDateTime;
import java.util.List;

public record ActivityDialogueSceneResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long sceneId,
        String type,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String place,
        List<ActivitySpeakerResponse> with,
        List<ActivityLineResponse> lines
) implements ActivitySceneResponse {

    public static ActivityDialogueSceneResponse from(ActivityDialogueSceneResult result) {
        return new ActivityDialogueSceneResponse(
                result.sceneId(),
                result.type(),
                result.startsAt(),
                result.endsAt(),
                result.place(),
                result.with().stream().map(ActivitySpeakerResponse::from).toList(),
                result.lines().stream().map(ActivityLineResponse::from).toList()
        );
    }
}
