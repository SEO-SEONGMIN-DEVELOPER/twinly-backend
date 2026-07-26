package com.nidus.twinly.connection.entity;

import org.hibernate.annotations.DynamicUpdate;
import com.nidus.twinly.connection.domain.ConnectionType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@DynamicUpdate
@Table(name = "connection_tickets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConnectionTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private UUID ticket;

    @Enumerated(EnumType.STRING)
    private ConnectionType connectionType;

    private Instant expiresAt;

    private Instant usedAt;

    private Instant createdAt;

    public static ConnectionTicket create(Long userId, ConnectionType connectionType, Instant expiresAt) {
        ConnectionTicket connectionTicket = new ConnectionTicket();

        connectionTicket.userId = userId;
        connectionTicket.ticket = UUID.randomUUID();
        connectionTicket.connectionType = connectionType;
        connectionTicket.expiresAt = expiresAt;
        connectionTicket.createdAt = Instant.now();

        return connectionTicket;
    }
}