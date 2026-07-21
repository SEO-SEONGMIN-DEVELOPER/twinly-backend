package com.nidus.twinly.me.dto.request;

import jakarta.validation.constraints.NotNull;

public record MeRevokeConsentsItemRequest(
        @NotNull Long policyId,
        @NotNull Integer version
) {
}
