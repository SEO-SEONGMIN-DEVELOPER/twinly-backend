package com.nidus.twinly.anon.repository;

import org.springframework.data.repository.CrudRepository;
import com.nidus.twinly.anon.entity.AnonSession;

public interface AnonSessionRepository extends CrudRepository<AnonSession, Long> {
}