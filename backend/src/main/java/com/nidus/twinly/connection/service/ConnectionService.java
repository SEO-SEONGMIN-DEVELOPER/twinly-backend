package com.nidus.twinly.connection.service;

import com.nidus.twinly.connection.dto.command.ConnectionTokenCommand;
import com.nidus.twinly.connection.dto.result.ConnectionTokenResult;
import com.nidus.twinly.connection.entity.ConnectionTicket;
import com.nidus.twinly.connection.repository.ConnectionTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConnectionService {

    private static final Duration TICKET_TTL = Duration.ofSeconds(30);

    private final ConnectionTicketRepository connectionTicketRepository;

    @Transactional
    public ConnectionTokenResult token(Long userId, ConnectionTokenCommand command) {
        Instant expiresAt = Instant.now().plus(TICKET_TTL);

        ConnectionTicket connectionTicket = ConnectionTicket.create(userId, command.connectionType(), expiresAt);
        connectionTicketRepository.save(connectionTicket);

        return new ConnectionTokenResult(connectionTicket.getTicket(), connectionTicket.getConnectionType(), connectionTicket.getExpiresAt());
    }

    @Transactional
    public Optional<Long> resolveTicket(UUID ticket) {
        return connectionTicketRepository.findByTicket(ticket)
                .filter(t -> t.getUsedAt() == null)
                .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
                .map(t -> {
                    t.use();
                    return t.getUserId();
                });
    }
}
