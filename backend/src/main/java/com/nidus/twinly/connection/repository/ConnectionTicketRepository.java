package com.nidus.twinly.connection.repository;

import com.nidus.twinly.connection.entity.ConnectionTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConnectionTicketRepository extends JpaRepository<ConnectionTicket, Long> {

    Optional<ConnectionTicket> findByTicket(UUID ticket);
}