package com.nidus.twinly.auth.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record AuthSignupRequest(
        @NotNull @Valid VerifiedTokenRequest verifiedToken
) {
}
