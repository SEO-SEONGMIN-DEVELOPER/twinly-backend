package com.nidus.twinly.common.websocket.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum WebSocketBodyType {

    @JsonProperty("chat.message.send") CHAT_MESSAGE_SEND,
    @JsonProperty("chat.read.advance") CHAT_READ_ADVANCE,

    @JsonProperty("chat.message.created") CHAT_MESSAGE_CREATED,
    @JsonProperty("chat.message.committed") CHAT_MESSAGE_COMMITTED,
    @JsonProperty("chat.message.rejected") CHAT_MESSAGE_REJECTED,
    @JsonProperty("chat.read.committed") CHAT_READ_COMMITTED,
    @JsonProperty("chat.read.rejected") CHAT_READ_REJECTED,
    @JsonProperty("chat.read.advanced") CHAT_READ_ADVANCED,
    @JsonProperty("chat.changed") CHAT_CHANGED,
    @JsonProperty("season.changed") SEASON_CHANGED,
    @JsonProperty("connection.draining") CONNECTION_DRAINING
}
