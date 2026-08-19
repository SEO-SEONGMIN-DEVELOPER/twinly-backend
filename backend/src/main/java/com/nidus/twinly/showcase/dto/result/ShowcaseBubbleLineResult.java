package com.nidus.twinly.showcase.dto.result;

import java.time.OffsetDateTime;

public record ShowcaseBubbleLineResult(
        String t,
        Long userRef,
        String action,
        String text,
        OffsetDateTime occursAt
) implements ShowcaseLineResult {
}
