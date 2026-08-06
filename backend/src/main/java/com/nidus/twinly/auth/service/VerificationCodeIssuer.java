package com.nidus.twinly.auth.service;

import com.nidus.twinly.auth.config.TestVerificationProperties;
import com.nidus.twinly.common.aws.ses.SesService;
import com.nidus.twinly.common.domain.VerificationType;
import com.nidus.twinly.common.solapi.SolapiService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
public class VerificationCodeIssuer {

    private static final int CODE_EXPIRES_MINUTES = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SesService sesService;
    private final SolapiService solapiService;
    private final TestVerificationProperties testVerificationProperties;

    public String issue(String contact) {
        if (testVerificationProperties.matches(contact)) {
            return testVerificationProperties.code();
        }

        int number = RANDOM.nextInt(1_000_000);
        return String.format("%06d", number);
    }

    public Instant codeExpiresAt() {
        return Instant.now().plus(CODE_EXPIRES_MINUTES, ChronoUnit.MINUTES);
    }

    public void send(VerificationType type, String contact, String code) {
        if (testVerificationProperties.matches(contact)) {
            return;
        }

        if (type == VerificationType.EMAIL) {
            sesService.send(
                    contact,
                    "[트윈리] 인증번호 발송",
                    "인증번호는 [%s] 입니다. %d분 이내에 입력해주세요.".formatted(code, CODE_EXPIRES_MINUTES)
            );
            return;
        }

        solapiService.send(
                contact,
                "[트윈리] 인증번호는 [%s] 입니다. %d분 이내에 입력해주세요.".formatted(code, CODE_EXPIRES_MINUTES)
        );
    }
}
