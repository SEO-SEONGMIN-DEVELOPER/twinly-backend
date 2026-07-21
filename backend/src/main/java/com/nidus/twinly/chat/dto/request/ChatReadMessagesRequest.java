package com.nidus.twinly.chat.dto.request;

import jakarta.validation.constraints.NotNull;

public record ChatReadMessagesRequest(
        @NotNull Long lastMessageId
) {
}
