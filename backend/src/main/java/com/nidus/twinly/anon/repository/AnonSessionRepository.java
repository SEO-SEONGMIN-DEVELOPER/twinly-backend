package com.nidus.twinly.anon.repository;

import com.nidus.twinly.anon.entity.AnonSession;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface AnonSessionRepository extends CrudRepository<AnonSession, Long> {

    Optional<AnonSession> findByToken(UUID token);
}