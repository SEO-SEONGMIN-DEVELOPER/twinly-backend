package com.nidus.twinly.common.websocket.dto;

import com.nidus.twinly.common.websocket.domain.WebSocketBodyKind;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record WebSocketCommandResultBody<T>(
        Integer v,
        @Schema(allowableValues = "command-result")
        String kind,
        WebSocketBodyType type,
        String commandId,
        Instant occurredAt,
        T payload
) implements WebSocketResponseBody {

    public static <T> WebSocketCommandResultBody<T> of(WebSocketBodyType type, String commandId, T payload) {
        return new WebSocketCommandResultBody<>(
                VERSION, WebSocketBodyKind.COMMAND_RESULT, type, commandId, Instant.now(), payload);
    }
}
