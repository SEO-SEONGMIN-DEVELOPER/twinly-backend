package com.nidus.twinly.connection.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ConnectionType {
    @JsonProperty("ws")  WS,
    @JsonProperty("sse") SSE
}
