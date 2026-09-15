package com.nidus.twinly.auth.dto.command;

import com.nidus.twinly.auth.dto.request.AuthIdentityVerifyRequest;

public record AuthIdentityVerifyCommand(
        String webTransactionId
) {

    public static AuthIdentityVerifyCommand from(AuthIdentityVerifyRequest request) {
        return new AuthIdentityVerifyCommand(request.webTransactionId());
    }
}
