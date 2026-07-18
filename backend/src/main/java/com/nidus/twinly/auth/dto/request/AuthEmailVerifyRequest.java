package com.nidus.twinly.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AuthEmailVerifyRequest(
        @NotNull UUID emailVerificationToken,
        @NotBlank String code
) {
}
