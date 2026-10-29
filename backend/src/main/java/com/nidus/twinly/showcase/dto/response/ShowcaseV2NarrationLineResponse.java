package com.nidus.twinly.showcase.dto.response;

import com.nidus.twinly.showcase.dto.result.ShowcaseNarrationLineResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

public record ShowcaseV2NarrationLineResponse(
        String t,
        String text,
        OffsetDateTime occursAt,
        @Schema(nullable = true)
        String speaker
) implements ShowcaseV2LineResponse {

    public static ShowcaseV2NarrationLineResponse from(ShowcaseNarrationLineResult result) {
        return new ShowcaseV2NarrationLineResponse(result.t(), result.text(), result.occursAt(), result.speaker());
    }
}
