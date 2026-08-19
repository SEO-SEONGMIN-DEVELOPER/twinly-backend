package com.nidus.twinly.showcase.dto.response;

import com.nidus.twinly.showcase.dto.result.ShowcaseNarrationLineResult;

import java.time.OffsetDateTime;

public record ShowcaseNarrationLineResponse(
        String t,
        String text,
        OffsetDateTime occursAt
) implements ShowcaseLineResponse {

    public static ShowcaseNarrationLineResponse from(ShowcaseNarrationLineResult result) {
        return new ShowcaseNarrationLineResponse(result.t(), result.text(), result.occursAt());
    }
}
