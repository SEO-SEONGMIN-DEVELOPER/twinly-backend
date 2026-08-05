package com.nidus.twinly.connection.dto.command;

import com.nidus.twinly.connection.domain.ConnectionDrainingReason;
import com.nidus.twinly.connection.dto.request.ConnectionDrainingRequest;

public record ConnectionDrainingCommand(
        ConnectionDrainingReason reason,
        Long retryAfterMs
) {

    public static ConnectionDrainingCommand from(ConnectionDrainingRequest request) {
        return new ConnectionDrainingCommand(request.reason(), request.retryAfterMs());
    }
}
