package com.nidus.twinly.connection.dto.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nidus.twinly.connection.domain.ConnectionDrainingReason;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConnectionDrainingPayload(
        ConnectionDrainingReason reason,
        Long retryAfterMs
) {
}
