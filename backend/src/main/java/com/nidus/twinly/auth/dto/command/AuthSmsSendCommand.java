package com.nidus.twinly.auth.dto.command;

import com.nidus.twinly.auth.dto.request.AuthSmsSendRequest;

public record AuthSmsSendCommand(
        String phone
) {

    public static AuthSmsSendCommand from(AuthSmsSendRequest request) {
        return new AuthSmsSendCommand(request.phone());
    }
}
