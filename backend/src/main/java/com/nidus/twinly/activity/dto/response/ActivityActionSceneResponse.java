package com.nidus.twinly.activity.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.activity.dto.result.ActivityActionSceneResult;

import java.time.Instant;
import java.util.List;

public record ActivityActionSceneResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long sceneId,
        String type,
        Instant startsAt,
        Instant endsAt,
        String place,
        List<ActivitySpeakerResponse> with,
        String narration,
        String mind
) implements ActivitySceneResponse {

    public static ActivityActionSceneResponse from(ActivityActionSceneResult result) {
        return new ActivityActionSceneResponse(
                result.sceneId(),
                result.type(),
                result.startsAt(),
                result.endsAt(),
                result.place(),
                result.with().stream().map(ActivitySpeakerResponse::from).toList(),
                result.narration(),
                result.mind()
        );
    }
}
