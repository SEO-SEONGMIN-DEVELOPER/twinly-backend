package com.nidus.twinly.auth.dto.command;

import com.nidus.twinly.auth.dto.request.AuthEmailVerifyRequest;

import java.util.UUID;

public record AuthEmailVerifyCommand(
        UUID emailVerificationToken,
        String code
) {

    public static AuthEmailVerifyCommand from(AuthEmailVerifyRequest request) {
        return new AuthEmailVerifyCommand(request.emailVerificationToken(), request.code());
    }
}
