package com.nidus.twinly.showcase.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.showcase.dto.result.ShowcaseBubbleLineResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

public record ShowcaseBubbleLineResponse(
        String t,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userRef,
        @Schema(nullable = true)
        String action,
        String text,
        OffsetDateTime occursAt
) implements ShowcaseLineResponse {

    public static ShowcaseBubbleLineResponse from(ShowcaseBubbleLineResult result) {
        return new ShowcaseBubbleLineResponse(result.t(), result.userRef(), result.action(), result.text(), result.occursAt());
    }
}
