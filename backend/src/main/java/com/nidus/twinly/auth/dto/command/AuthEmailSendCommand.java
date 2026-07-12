package com.nidus.twinly.auth.dto.command;

import com.nidus.twinly.auth.dto.request.AuthEmailSendRequest;

public record AuthEmailSendCommand(
        String email
) {

    public static AuthEmailSendCommand from(AuthEmailSendRequest request) {
        return new AuthEmailSendCommand(request.email());
    }
}
