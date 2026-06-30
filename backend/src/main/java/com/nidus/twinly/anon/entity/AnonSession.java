package com.nidus.twinly.anon.entity;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("anon_sessions")
public record AnonSession(@Id Long id, UUID token, Instant expiresAt) {
}