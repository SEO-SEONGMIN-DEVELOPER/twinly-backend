package com.nidus.twinly.app.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record MaintenanceUpdateRequest(
        @NotNull Boolean active,
        @Size(max = 200)
        @Schema(nullable = true)
        String message,
        @Schema(nullable = true)
        Instant until
) {
}
