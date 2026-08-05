package com.nidus.twinly.connection.service;

import com.nidus.twinly.connection.domain.ConnectionType;
import com.nidus.twinly.connection.dto.command.ConnectionDrainingCommand;
import com.nidus.twinly.connection.dto.command.ConnectionTokenCommand;
import com.nidus.twinly.connection.dto.result.ConnectionTicketResolveResult;
import com.nidus.twinly.connection.dto.result.ConnectionTokenResult;
import com.nidus.twinly.connection.entity.ConnectionTicket;
import com.nidus.twinly.connection.notifier.ConnectionControlNotifier;
import com.nidus.twinly.connection.repository.ConnectionTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConnectionService {

    private static final Duration TICKET_TTL = Duration.ofSeconds(60);

    private final ConnectionTicketRepository connectionTicketRepository;
    private final ConnectionControlNotifier connectionControlNotifier;

    public void notifyDraining(ConnectionDrainingCommand command) {
        connectionControlNotifier.notifyDraining(command.reason(), command.retryAfterMs());
    }

    @Transactional
    public ConnectionTokenResult token(Long userId, ConnectionTokenCommand command) {
        Instant expiresAt = Instant.now().plus(TICKET_TTL);

        ConnectionTicket connectionTicket = ConnectionTicket.create(userId, command.connectionType(), expiresAt);
        connectionTicketRepository.save(connectionTicket);

        return new ConnectionTokenResult(connectionTicket.getTicket(), connectionTicket.getConnectionType(), connectionTicket.getExpiresAt());
    }

    @Transactional
    public ConnectionTicketResolveResult resolveTicket(UUID ticket, ConnectionType requiredConnectionType) {
        ConnectionTicket connectionTicket = connectionTicketRepository.findByTicket(ticket).orElse(null);

        if (connectionTicket == null) {
            return ConnectionTicketResolveResult.invalid();
        }

        if (connectionTicketRepository.consume(ticket) == 0) {
            return ConnectionTicketResolveResult.invalid();
        }

        if (connectionTicket.getConnectionType() != requiredConnectionType) {
            return ConnectionTicketResolveResult.scopeMismatch();
        }

        return ConnectionTicketResolveResult.authorized(connectionTicket.getUserId());
    }
}
