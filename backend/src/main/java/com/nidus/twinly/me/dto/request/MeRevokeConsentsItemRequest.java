package com.nidus.twinly.me.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

public record MeRevokeConsentsItemRequest(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @NotNull Long policyId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @NotNull Integer version
) {
}
