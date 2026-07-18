package com.nidus.twinly.activity.dto.response;

import com.nidus.twinly.activity.dto.result.ActivityBubbleDialogueResult;

public record ActivityBubbleDialogueResponse(
        String t,
        ActivitySpeakerResponse speaker,
        String action,
        String text
) implements ActivityDialogueResponse {

    public static ActivityBubbleDialogueResponse from(ActivityBubbleDialogueResult result) {
        return new ActivityBubbleDialogueResponse(
                result.t(),
                ActivitySpeakerResponse.from(result.speaker()),
                result.action(),
                result.text()
        );
    }
}
