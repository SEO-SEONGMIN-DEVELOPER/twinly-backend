package com.nidus.twinly.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AuthSmsSendRequest(
        @NotBlank String phone
) {
}
