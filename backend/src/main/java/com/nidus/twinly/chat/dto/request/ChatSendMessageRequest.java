package com.nidus.twinly.chat.dto.request;

import jakarta.validation.constraints.NotNull;

public record ChatSendMessageRequest(
        @NotNull String text,
        @NotNull String clientMsgId
) {
}
