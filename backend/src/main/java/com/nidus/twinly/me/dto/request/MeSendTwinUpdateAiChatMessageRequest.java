package com.nidus.twinly.me.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MeSendTwinUpdateAiChatMessageRequest(
        @NotNull UUID updateId,
        @NotBlank String message,
        @NotNull Integer turnIndex
) {
}
