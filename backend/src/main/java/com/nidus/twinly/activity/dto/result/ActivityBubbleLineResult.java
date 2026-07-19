package com.nidus.twinly.activity.dto.result;

public record ActivityBubbleLineResult(
        String t,
        ActivitySpeakerResult speaker,
        String action,
        String text
) implements ActivityLineResult {
}
