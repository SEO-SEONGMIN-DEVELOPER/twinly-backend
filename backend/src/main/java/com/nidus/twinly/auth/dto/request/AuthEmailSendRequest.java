package com.nidus.twinly.auth.dto.request;

import jakarta.validation.constraints.NotNull;

public record AuthEmailSendRequest(
        @NotNull String email
) {
}
