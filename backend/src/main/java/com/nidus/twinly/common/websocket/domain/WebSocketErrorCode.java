package com.nidus.twinly.common.websocket.domain;

import com.nidus.twinly.common.web.ErrorCode;

import java.util.Arrays;
import java.util.List;

public enum WebSocketErrorCode {

    INVALID_REQUEST(true),
    CLIENT_MSG_ID_CONFLICT(true),
    INVALID_MESSAGE_CURSOR(true),
    ROOM_NOT_FOUND(true),
    MATCH_NOT_FOUND(true),
    PARTICIPATION_NOT_FOUND(true),
    NOT_A_PARTICIPANT(true),
    NOT_ACTIVE_PARTICIPANT(true),
    ROOM_CLOSED(true),
    ROOM_ENTRY_NOT_AGREED(true),
    TEXT_SIZE_LIMIT_EXCEEDED(true),
    INTERNAL(true),

    FRAME_REJECTED(false),
    SEND_FAILED(false),
    RELAY_PUBLISH_FAILED(false),
    RELAY_RECEIVE_FAILED(false);

    private final boolean clientVisible;

    WebSocketErrorCode(boolean clientVisible) {
        this.clientVisible = clientVisible;
    }

    public boolean isClientVisible() {
        return clientVisible;
    }

    public static List<String> serverOnlyNames() {
        return Arrays.stream(values())
                .filter(code -> !code.clientVisible)
                .map(Enum::name)
                .toList();
    }

    public static WebSocketErrorCode from(ErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_REQUEST -> INVALID_REQUEST;
            case ROOM_NOT_FOUND -> ROOM_NOT_FOUND;
            case MATCH_NOT_FOUND -> MATCH_NOT_FOUND;
            case CHAT_PARTICIPATION_NOT_FOUND -> PARTICIPATION_NOT_FOUND;
            case NOT_MATCH_PARTICIPANT -> NOT_A_PARTICIPANT;
            case NOT_ACTIVE_ROOM_PARTICIPANT -> NOT_ACTIVE_PARTICIPANT;
            case ROOM_CLOSED -> ROOM_CLOSED;
            case ROOM_ENTRY_NOT_AGREED -> ROOM_ENTRY_NOT_AGREED;
            case CLIENT_MSG_ID_CONFLICT -> CLIENT_MSG_ID_CONFLICT;
            case MESSAGE_LENGTH_EXCEEDED -> TEXT_SIZE_LIMIT_EXCEEDED;
            case MESSAGE_NOT_IN_ROOM -> INVALID_MESSAGE_CURSOR;
            default -> INTERNAL;
        };
    }
}
