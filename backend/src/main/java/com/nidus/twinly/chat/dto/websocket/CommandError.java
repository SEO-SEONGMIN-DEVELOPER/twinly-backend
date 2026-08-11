package com.nidus.twinly.chat.dto.websocket;

import com.nidus.twinly.common.websocket.domain.WebSocketErrorCode;
import org.springframework.util.Assert;

public record CommandError(
        WebSocketErrorCode code,
        String message,
        Boolean retryable
) {
    public CommandError {
        Assert.isTrue(code.isClientVisible(), "클라이언트에 노출할 수 없는 에러 코드입니다: " + code);
    }
}
