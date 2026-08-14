package com.nidus.twinly.connection.dto.request;

import com.nidus.twinly.connection.domain.ConnectionDrainingReason;
import com.nidus.twinly.connection.domain.ConnectionDrainingScope;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ConnectionDrainingRequest(
        @NotNull ConnectionDrainingReason reason,
        @Positive
        @Schema(nullable = true)
        Long retryAfterMs,
        @NotNull ConnectionDrainingScope scope
) {
}
