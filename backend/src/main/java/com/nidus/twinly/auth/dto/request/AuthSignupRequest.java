package com.nidus.twinly.auth.dto.request;

import com.nidus.twinly.auth.domain.VerifiedToken;

public record AuthSignupRequest(
        VerifiedToken verifiedToken
) {
}