package com.nidus.twinly.app.dto.response;

import com.nidus.twinly.common.web.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record MaintenanceResponse(
        String code,
        @Schema(nullable = true) String message,
        @Schema(nullable = true) Instant until
) {
    public static MaintenanceResponse of(String message, Instant until) {
        return new MaintenanceResponse(ErrorCode.MAINTENANCE.name(), message, until);
    }
}
