package com.nidus.twinly.me.dto.request;

import jakarta.validation.constraints.NotBlank;

public record MeChangeProfileNicknameRequest(
        @NotBlank String nickname
) {
}
