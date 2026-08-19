package com.nidus.twinly.showcase.dto.result;

import java.time.OffsetDateTime;

public record ShowcaseNarrationLineResult(
        String t,
        String text,
        OffsetDateTime occursAt
) implements ShowcaseLineResult {
}
