package com.nidus.twinly.auth.dto.response;

import com.nidus.twinly.auth.dto.result.AuthEmailVerifyResult;

import java.util.UUID;

public record AuthEmailVerifyResponse(
        UUID emailVerifiedToken
) {

    public static AuthEmailVerifyResponse from(AuthEmailVerifyResult result) {
        return new AuthEmailVerifyResponse(result.emailVerifiedToken());
    }
}
