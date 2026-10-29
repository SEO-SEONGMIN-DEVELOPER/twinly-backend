package com.nidus.twinly.common.scene;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

public record StoredSceneNarrationLine(
        String t,
        String text,
        LocalDateTime occursAt,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String speaker
) implements StoredSceneLine {
}
