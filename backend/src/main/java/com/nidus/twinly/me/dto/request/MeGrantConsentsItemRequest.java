package com.nidus.twinly.me.dto.request;

import jakarta.validation.constraints.NotBlank;

public record MeGrantConsentsItemRequest(
        @NotBlank String policyId,
        @NotBlank String version
) {
}
