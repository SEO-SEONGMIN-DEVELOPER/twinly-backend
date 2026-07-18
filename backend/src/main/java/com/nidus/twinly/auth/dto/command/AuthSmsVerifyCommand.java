package com.nidus.twinly.auth.dto.command;

import com.nidus.twinly.auth.dto.request.AuthSmsVerifyRequest;

import java.util.UUID;

public record AuthSmsVerifyCommand (
        UUID smsVerificationToken,
        String code
) implements VerifyCommand {

    @Override
    public UUID verificationToken() {
        return smsVerificationToken();
    }

    @Override
    public String value() {
        return code();
    }

    public static AuthSmsVerifyCommand from(AuthSmsVerifyRequest request) {
        return new AuthSmsVerifyCommand(request.smsVerificationToken(), request.code());
    }
}
