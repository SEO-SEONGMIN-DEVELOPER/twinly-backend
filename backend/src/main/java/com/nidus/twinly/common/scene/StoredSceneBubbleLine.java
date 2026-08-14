package com.nidus.twinly.common.scene;

import java.time.LocalDateTime;

public record StoredSceneBubbleLine(
        String t,
        Long userId,
        String action,
        String text,
        LocalDateTime occursAt
) implements StoredSceneLine {
}
