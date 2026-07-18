package com.nidus.twinly.auth.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record VerifiedTokenRequest(
        @NotNull UUID smsVerifiedToken,
        @NotNull UUID emailVerifiedToken
) {
}
