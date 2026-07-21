package com.nidus.twinly.auth.dto.request;

import jakarta.validation.constraints.NotNull;

public record AuthLogoutRequest(
        @NotNull String refreshToken
) {
}
