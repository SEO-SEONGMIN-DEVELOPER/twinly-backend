package com.nidus.twinly.auth.dto.command;

import com.nidus.twinly.auth.dto.request.AuthSignupRequest;

public record AuthSignupCommand(
        VerifiedTokenCommand verifiedToken
) {

    public static AuthSignupCommand from(AuthSignupRequest request) {
        return new AuthSignupCommand(new VerifiedTokenCommand(request.verifiedToken().smsVerifiedToken(), request.verifiedToken().emailVerifiedToken()));
    }
}
