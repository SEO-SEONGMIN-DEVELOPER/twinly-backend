package com.nidus.twinly.connection.dto.request;

import jakarta.validation.constraints.NotNull;
import com.nidus.twinly.connection.domain.ConnectionType;

public record ConnectionTokenRequest(
        @NotNull ConnectionType connectionType
) {
}
