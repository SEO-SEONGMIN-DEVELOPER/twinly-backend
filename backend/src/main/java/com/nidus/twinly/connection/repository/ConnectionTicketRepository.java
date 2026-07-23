package com.nidus.twinly.connection.repository;

import com.nidus.twinly.connection.entity.ConnectionTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ConnectionTicketRepository extends JpaRepository<ConnectionTicket, Long> {

    Optional<ConnectionTicket> findByTicket(UUID ticket);

    @Modifying
    @Query(value = """
            UPDATE connection_tickets
            SET used_at = now()
            WHERE ticket = :ticket
              AND used_at IS NULL
              AND expires_at > now()
            """, nativeQuery = true)
    int consume(@Param("ticket") UUID ticket);
}
