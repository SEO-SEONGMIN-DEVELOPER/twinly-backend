package com.nidus.twinly.activity.dto.response;

import com.nidus.twinly.activity.dto.result.ActivityNarrationLineResult;

public record ActivityNarrationLineResponse(
        String t,
        String text
) implements ActivityLineResponse {

    public static ActivityNarrationLineResponse from(ActivityNarrationLineResult result) {
        return new ActivityNarrationLineResponse(result.t(), result.text());
    }
}
