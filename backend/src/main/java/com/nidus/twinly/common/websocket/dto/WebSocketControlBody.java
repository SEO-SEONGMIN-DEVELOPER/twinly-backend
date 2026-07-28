package com.nidus.twinly.common.websocket.dto;

import com.nidus.twinly.common.websocket.domain.WebSocketBodyKind;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;
import io.swagger.v3.oas.annotations.media.Schema;

public record WebSocketControlBody<T>(
        Integer v,
        @Schema(allowableValues = "control")
        String kind,
        WebSocketBodyType type,
        T payload
) implements WebSocketResponseBody {

    public static <T> WebSocketControlBody<T> of(WebSocketBodyType type, T payload) {
        return new WebSocketControlBody<>(VERSION, WebSocketBodyKind.CONTROL, type, payload);
    }
}
