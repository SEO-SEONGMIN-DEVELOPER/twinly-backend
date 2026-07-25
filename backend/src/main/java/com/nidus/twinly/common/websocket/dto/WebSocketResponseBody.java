package com.nidus.twinly.common.websocket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyKind;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebSocketResponseBody(
        Integer v,
        String kind,
        WebSocketBodyType type,
        String eventId,
        String commandId,
        Instant occurredAt,
        Object payload
) {

    private static final int VERSION = 1;

    public static WebSocketResponseBody event(WebSocketBodyType type, Object payload) {
        return new WebSocketResponseBody(VERSION, WebSocketBodyKind.EVENT, type, UUID.randomUUID().toString(), null, Instant.now(), payload);
    }

    public static WebSocketResponseBody control(WebSocketBodyType type, Object payload) {
        return new WebSocketResponseBody(VERSION, WebSocketBodyKind.CONTROL, type, null, null, null, payload);
    }

    public static WebSocketResponseBody commandResult(String commandId, WebSocketBodyType type, Object payload) {
        return new WebSocketResponseBody(VERSION, WebSocketBodyKind.COMMAND_RESULT, type, null, commandId, Instant.now(), payload);
    }
}
