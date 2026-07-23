package com.nidus.twinly.common.websocket.dto;

import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record WebSocketRequestBody<T>(
        @NotNull Integer v,
        @NotNull String kind,
        @NotNull WebSocketBodyType type,
        @NotNull String commandId,
        @NotNull @Valid T payload
) {
}
