package com.nidus.twinly.anon.service;

import java.util.UUID;
import java.time.Instant;
import java.time.Duration;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import com.nidus.twinly.anon.dto.AnonStartResponse;
import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.anon.repository.AnonSessionRepository;

@Service
@RequiredArgsConstructor
public class AnonService {

    private static final Duration TTL = Duration.ofDays(14);

    private final AnonSessionRepository anonSessionRepository;

    public AnonStartResponse start() {
        UUID token = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(TTL);

        anonSessionRepository.save(new AnonSession(null, token, expiresAt));
        return new AnonStartResponse(token, TTL.toSeconds());
    }
}