package com.nidus.twinly.common.websocket.dto;

import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;

public sealed interface WebSocketResponseBody
        permits WebSocketEventBody, WebSocketCommandResultBody, WebSocketControlBody {

    int VERSION = 1;

    Integer v();

    String kind();

    WebSocketBodyType type();

    Object payload();
}
