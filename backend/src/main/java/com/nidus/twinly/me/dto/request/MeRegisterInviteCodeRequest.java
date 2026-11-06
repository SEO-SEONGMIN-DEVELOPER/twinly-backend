package com.nidus.twinly.me.dto.request;

import jakarta.validation.constraints.NotBlank;

public record MeRegisterInviteCodeRequest(
        @NotBlank String code
) {
}
