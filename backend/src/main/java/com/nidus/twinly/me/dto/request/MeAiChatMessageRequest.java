package com.nidus.twinly.me.dto.request;

import jakarta.validation.constraints.NotNull;

public record MeAiChatMessageRequest(
        @NotNull String message,
        @NotNull Integer turnIndex
) {
}
