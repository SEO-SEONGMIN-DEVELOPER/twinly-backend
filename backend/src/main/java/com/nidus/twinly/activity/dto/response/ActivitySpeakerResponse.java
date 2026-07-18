package com.nidus.twinly.activity.dto.response;

import com.nidus.twinly.activity.dto.result.ActivitySpeakerResult;

public record ActivitySpeakerResponse(
        Long userId,
        String userName
) {

    public static ActivitySpeakerResponse from(ActivitySpeakerResult result) {
        return new ActivitySpeakerResponse(result.userId(), result.userName());
    }
}
