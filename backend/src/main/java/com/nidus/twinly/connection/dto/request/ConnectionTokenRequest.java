package com.nidus.twinly.connection.dto.request;

import com.nidus.twinly.connection.domain.ConnectionType;

public record ConnectionTokenRequest(
        ConnectionType connectionType
) {
}
