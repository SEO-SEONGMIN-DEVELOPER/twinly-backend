package com.nidus.twinly.chat.dto.websocket;

import com.nidus.twinly.chat.domain.CommandErrorCode;

public record CommandError(
        CommandErrorCode code,
        String message,
        Boolean retryable
) {
}
