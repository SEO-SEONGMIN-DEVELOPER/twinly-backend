package com.nidus.twinly.common.websocket.dto;

import com.nidus.twinly.common.websocket.domain.WebSocketBodyKind;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record WebSocketEventBody<T>(
        Integer v,
        @Schema(allowableValues = "event")
        String kind,
        WebSocketBodyType type,
        String eventId,
        Instant occurredAt,
        T payload
) implements WebSocketResponseBody {

    public static <T> WebSocketEventBody<T> of(WebSocketBodyType type, T payload) {
        return new WebSocketEventBody<>(
                VERSION, WebSocketBodyKind.EVENT, type, UUID.randomUUID().toString(), Instant.now(), payload);
    }
}
