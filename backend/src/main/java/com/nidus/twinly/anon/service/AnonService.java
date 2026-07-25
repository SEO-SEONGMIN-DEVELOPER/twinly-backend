package com.nidus.twinly.anon.service;

import com.nidus.twinly.anon.dto.result.AnonStartResult;
import com.nidus.twinly.anon.dto.snapshot.AnonSessionSnapshot;
import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ANON_SESSION));

        if (anonSession.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }

        return AnonSessionSnapshot.from(anonSession);
    }
}
