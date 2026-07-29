package com.nidus.twinly.me.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MeProfileRequest(
        @NotBlank @Size(max = 50) String affiliation
) {
}
