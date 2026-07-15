package com.nidus.twinly.common.websocket.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum WebSocketEventType {
    @JsonProperty("chatMsg") CHAT_MSG,
    @JsonProperty("seasonChange") SEASON_CHANGE
}