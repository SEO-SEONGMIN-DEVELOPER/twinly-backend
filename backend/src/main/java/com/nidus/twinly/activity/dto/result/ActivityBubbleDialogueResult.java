package com.nidus.twinly.activity.dto.result;

public record ActivityBubbleDialogueResult(
        String t,
        ActivitySpeakerResult speaker,
        String action,
        String text
) implements ActivityDialogueResult {
}
