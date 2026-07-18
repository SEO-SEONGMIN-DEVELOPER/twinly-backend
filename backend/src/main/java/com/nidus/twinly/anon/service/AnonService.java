package com.nidus.twinly.anon.service;

import com.nidus.twinly.anon.dto.result.AnonStartResult;
import com.nidus.twinly.anon.dto.snapshot.AnonSessionSnapshot;
import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class AnonService {

    private static final Duration TTL = Duration.ofDays(14);

    private final AnonSessionRepository anonSessionRepository;

    public AnonStartResult start() {
        UUID token = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(TTL);

        anonSessionRepository.save(AnonSession.create(token, expiresAt));

        return new AnonStartResult(token, expiresAt);
    }

    public AnonSessionSnapshot resolveByToken(UUID token) {
        AnonSession anonSession = anonSessionRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 세션 토큰입니다"));

        if (anonSession.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다");
        }

        return AnonSessionSnapshot.from(anonSession);
    }
}
