package com.nidus.twinly.activity.dto.response;

import com.nidus.twinly.activity.dto.result.ActivityNarrationDialogueResult;

public record ActivityNarrationDialogueResponse(
        String t,
        String text
) implements ActivityDialogueResponse {

    public static ActivityNarrationDialogueResponse from(ActivityNarrationDialogueResult result) {
        return new ActivityNarrationDialogueResponse(result.t(), result.text());
    }
}
