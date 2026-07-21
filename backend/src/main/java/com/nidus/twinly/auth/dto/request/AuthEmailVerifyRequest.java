package com.nidus.twinly.auth.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AuthEmailVerifyRequest(
        @NotNull UUID emailVerificationToken,
        @NotNull String code
) {
}
