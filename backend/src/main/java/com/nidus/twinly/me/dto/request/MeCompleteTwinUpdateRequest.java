package com.nidus.twinly.me.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MeCompleteTwinUpdateRequest(
        @NotNull UUID updateId
) {
}
