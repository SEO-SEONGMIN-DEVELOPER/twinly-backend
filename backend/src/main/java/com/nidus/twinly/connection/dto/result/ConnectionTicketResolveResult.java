package com.nidus.twinly.connection.dto.result;

import com.nidus.twinly.connection.domain.ConnectionTicketStatus;

public record ConnectionTicketResolveResult(
        ConnectionTicketStatus status,
        Long userId
) {

    public static ConnectionTicketResolveResult invalid() {
        return new ConnectionTicketResolveResult(ConnectionTicketStatus.INVALID, null);
    }

    public static ConnectionTicketResolveResult scopeMismatch() {
        return new ConnectionTicketResolveResult(ConnectionTicketStatus.SCOPE_MISMATCH, null);
    }

    public static ConnectionTicketResolveResult authorized(Long userId) {
        return new ConnectionTicketResolveResult(ConnectionTicketStatus.AUTHORIZED, userId);
    }
}
