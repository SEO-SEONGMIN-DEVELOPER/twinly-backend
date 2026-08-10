package com.nidus.twinly.connection.dto.request;

import com.nidus.twinly.connection.domain.ConnectionDrainingReason;
import com.nidus.twinly.connection.domain.ConnectionDrainingScope;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ConnectionDrainingRequest(
        @NotNull ConnectionDrainingReason reason,
        @Positive Long retryAfterMs,
        ConnectionDrainingScope scope
) {
}
