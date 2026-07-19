package com.nidus.twinly.activity.dto.response;

import com.nidus.twinly.activity.dto.result.ActivityDialogueSceneResult;

import java.time.Instant;
import java.util.List;

public record ActivityDialogueSceneResponse(
        String type,
        Instant startsAt,
        Instant endsAt,
        String place,
        List<ActivitySpeakerResponse> with,
        List<ActivityLineResponse> lines
) implements ActivitySceneResponse {

    public static ActivityDialogueSceneResponse from(ActivityDialogueSceneResult result) {
        return new ActivityDialogueSceneResponse(
                result.type(),
                result.startsAt(),
                result.endsAt(),
                result.place(),
                result.with().stream().map(ActivitySpeakerResponse::from).toList(),
                result.lines().stream().map(ActivityLineResponse::from).toList()
        );
    }
}
