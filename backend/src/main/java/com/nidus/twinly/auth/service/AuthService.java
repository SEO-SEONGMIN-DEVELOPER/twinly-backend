package com.nidus.twinly.auth.service;

import com.nidus.twinly.auth.dto.command.AuthEmailSendCommand;
import com.nidus.twinly.auth.dto.result.AuthEmailSendResult;
import com.nidus.twinly.auth.entity.VerificationSession;
import com.nidus.twinly.auth.repository.VerificationSessionRepository;
import com.nidus.twinly.common.aws.ses.SesService;
import com.nidus.twinly.common.domain.VerificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int CODE_EXPIRES_MINUTES = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final VerificationSessionRepository verificationSessionRepository;
    private final SesService sesService;

    @Transactional
    public AuthEmailSendResult emailSend(AuthEmailSendCommand command) {
        String code = generateCode();
        Instant codeExpiresAt = Instant.now().plus(CODE_EXPIRES_MINUTES, ChronoUnit.MINUTES);

        VerificationSession session = VerificationSession.create(VerificationType.EMAIL, command.email(), code, codeExpiresAt);
        verificationSessionRepository.save(session);

        sesService.send(
                command.email(),
                "[트윈리] 인증번호 발송",
                "인증번호는 [%s] 입니다. %d분 이내에 입력해주세요.".formatted(code, CODE_EXPIRES_MINUTES)
        );

        return new AuthEmailSendResult(session.getVerificationToken().toString(), codeExpiresAt);
    }

    private String generateCode() {
        int number = RANDOM.nextInt(1_000_000);
        return String.format("%06d", number);
    }
}
