package com.nidus.twinly.me.dto.request;

import jakarta.validation.constraints.NotNull;

public record MeGrantConsentsItemRequest(
        @NotNull Long policyId,
        @NotNull Integer version
) {
}
