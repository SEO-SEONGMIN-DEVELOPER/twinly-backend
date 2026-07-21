package com.nidus.twinly.auth.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AuthSmsVerifyRequest(
        @NotNull UUID smsVerificationToken,
        @NotNull String code
) {
}
