package com.nidus.twinly.auth.dto.command;

import com.nidus.twinly.auth.domain.VerifiedToken;
import com.nidus.twinly.auth.dto.request.AuthSignupRequest;

public record AuthSignupCommand(
        VerifiedToken verifiedToken
) {

    public static AuthSignupCommand from(AuthSignupRequest request) {
        return new AuthSignupCommand(new VerifiedToken(request.verifiedToken().smsVerifiedToken(), request.verifiedToken().emailVerifiedToken()));
    }
}