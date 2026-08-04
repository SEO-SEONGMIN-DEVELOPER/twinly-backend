package com.nidus.twinly.common.scene;

public record SceneBubbleLine(
        String t,
        Long userId,
        String action,
        String text
) implements SceneLine {
}
