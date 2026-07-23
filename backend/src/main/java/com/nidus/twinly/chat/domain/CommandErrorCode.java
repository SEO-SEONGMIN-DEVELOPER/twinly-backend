package com.nidus.twinly.chat.domain;

public enum CommandErrorCode {

    CLIENT_MSG_ID_CONFLICT,
    INVALID_MESSAGE_CURSOR,
    ROOM_NOT_FOUND,
    NOT_A_PARTICIPANT,
    INTERNAL
}
