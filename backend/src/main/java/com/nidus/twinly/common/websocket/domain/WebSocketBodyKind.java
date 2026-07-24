package com.nidus.twinly.common.websocket.domain;

public final class WebSocketBodyKind {

    public static final String EVENT = "event";
    public static final String CONTROL = "control";
    public static final String COMMAND = "command";
    public static final String COMMAND_RESULT = "command-result";

    private WebSocketBodyKind() {
    }
}
