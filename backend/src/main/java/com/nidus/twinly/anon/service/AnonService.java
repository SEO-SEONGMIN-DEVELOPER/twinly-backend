package com.nidus.twinly.anon.service;

import com.nidus.twinly.anon.config.AnonProperties;
import com.nidus.twinly.anon.dto.result.AnonStartResult;
import com.nidus.twinly.anon.dto.snapshot.AnonSessionSnapshot;
import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnonService {

    /*
     * [멘토링 피드백 반영 완료]
     * 상수는 환경변수에서 가져오도록 변환
     */

    private final AnonProperties anonProperties;

    private final AnonSessionRepository anonSessionRepository;

    @Transactional
    public AnonStartResult start() {
        UUID token = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(anonProperties.sessionTtl());

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
