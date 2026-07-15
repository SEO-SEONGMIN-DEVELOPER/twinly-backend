package com.nidus.twinly.connection.dto.command;

import com.nidus.twinly.connection.domain.ConnectionType;
import com.nidus.twinly.connection.dto.request.ConnectionTokenRequest;

public record ConnectionTokenCommand(
        ConnectionType connectionType
) {

    public static ConnectionTokenCommand from(ConnectionTokenRequest request) {
        return new ConnectionTokenCommand(request.connectionType());
    }
}
