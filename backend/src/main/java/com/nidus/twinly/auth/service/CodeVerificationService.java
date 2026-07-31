package com.nidus.twinly.auth.service;

import com.nidus.twinly.auth.dto.command.VerifyCommand;
import com.nidus.twinly.auth.entity.VerificationSession;
import com.nidus.twinly.auth.repository.VerificationSessionRepository;
import com.nidus.twinly.common.domain.VerificationType;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class CodeVerificationService implements VerificationService {

    private static final int VERIFIED_TOKEN_EXPIRES_MINUTES = 5;

    private final VerificationSessionRepository verificationSessionRepository;

    public VerificationSession verify(VerifyCommand command, VerificationType type) {
        VerificationSession session = verificationSessionRepository.findByTypeAndVerificationToken(type, command.verificationToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND));

        if (session.getCodeExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_EXPIRED);
        }

        if (!session.getCode().equals(command.value())) {
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_MISMATCH);
        }

        if (session.getVerifiedAt() != null) {
            return session;
        }

        Instant verifiedTokenExpiresAt = Instant.now().plus(VERIFIED_TOKEN_EXPIRES_MINUTES, ChronoUnit.MINUTES);
        session.verify(verifiedTokenExpiresAt);

        return session;
    }
}
