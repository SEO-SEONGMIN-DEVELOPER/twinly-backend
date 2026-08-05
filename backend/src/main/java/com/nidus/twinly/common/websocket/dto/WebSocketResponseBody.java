package com.nidus.twinly.common.websocket.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyKind;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "kind",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = WebSocketEventBody.class, name = WebSocketBodyKind.EVENT),
        @JsonSubTypes.Type(value = WebSocketControlBody.class, name = WebSocketBodyKind.CONTROL),
        @JsonSubTypes.Type(value = WebSocketCommandResultBody.class, name = WebSocketBodyKind.COMMAND_RESULT)
})
public sealed interface WebSocketResponseBody
        permits WebSocketEventBody, WebSocketCommandResultBody, WebSocketControlBody {

    int VERSION = 1;

    Integer v();

    String kind();

    WebSocketBodyType type();

    Object payload();
}
