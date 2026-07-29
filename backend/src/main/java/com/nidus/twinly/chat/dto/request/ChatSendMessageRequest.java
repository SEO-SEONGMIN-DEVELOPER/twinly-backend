package com.nidus.twinly.chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatSendMessageRequest(
        @NotBlank String text,
        @NotBlank @Size(max = 255) String clientMsgId
) {
}
