package com.nidus.twinly.auth.dto.command;

import com.nidus.twinly.auth.dto.request.AuthLoginRequest;

import java.util.UUID;

public record AuthLoginCommand(
        UUID smsVerifiedToken
) {

    public static AuthLoginCommand from(AuthLoginRequest request) {
        return new AuthLoginCommand(request.smsVerifiedToken());
    }
}
