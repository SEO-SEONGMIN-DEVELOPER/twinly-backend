package com.nidus.twinly.activity.dto.response;

import com.nidus.twinly.activity.dto.result.ActivityBubbleLineResult;

public record ActivityBubbleLineResponse(
        String t,
        ActivitySpeakerResponse speaker,
        String action,
        String text
) implements ActivityLineResponse {

    public static ActivityBubbleLineResponse from(ActivityBubbleLineResult result) {
        return new ActivityBubbleLineResponse(
                result.t(),
                ActivitySpeakerResponse.from(result.speaker()),
                result.action(),
                result.text()
        );
    }
}
