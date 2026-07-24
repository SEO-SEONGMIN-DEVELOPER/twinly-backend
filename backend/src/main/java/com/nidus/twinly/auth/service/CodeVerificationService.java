package com.nidus.twinly.auth.service;

import com.nidus.twinly.auth.dto.command.VerifyCommand;
import com.nidus.twinly.auth.entity.VerificationSession;
import com.nidus.twinly.auth.repository.VerificationSessionRepository;
import com.nidus.twinly.common.domain.VerificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class CodeVerificationService implements VerificationService {

    private static final int VERIFIED_TOKEN_EXPIRES_MINUTES = 30;

    private final VerificationSessionRepository verificationSessionRepository;

    public VerificationSession verify(VerifyCommand command, VerificationType type) {
        VerificationSession session = verificationSessionRepository.findByTypeAndVerificationToken(type, command.verificationToken())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "유효하지 않은 인증 요청입니다."));

        if (session.getCodeExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "인증번호가 만료되었습니다.");
        }

        if (!session.getCode().equals(command.value())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "인증번호가 일치하지 않습니다.");
        }

        Instant verifiedTokenExpiresAt = Instant.now().plus(VERIFIED_TOKEN_EXPIRES_MINUTES, ChronoUnit.MINUTES);
        session.verify(verifiedTokenExpiresAt);

        return session;
    }
}
