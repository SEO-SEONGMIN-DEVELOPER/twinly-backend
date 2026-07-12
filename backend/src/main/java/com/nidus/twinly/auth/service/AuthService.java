package com.nidus.twinly.auth.service;

import com.nidus.twinly.auth.dto.command.AuthEmailSendCommand;
import com.nidus.twinly.auth.dto.command.AuthEmailVerifyCommand;
import com.nidus.twinly.auth.dto.command.AuthSmsSendCommand;
import com.nidus.twinly.auth.dto.result.AuthEmailSendResult;
import com.nidus.twinly.auth.dto.result.AuthEmailVerifyResult;
import com.nidus.twinly.auth.dto.result.AuthSmsSendResult;
import com.nidus.twinly.auth.entity.VerificationSession;
import com.nidus.twinly.auth.repository.VerificationSessionRepository;
import com.nidus.twinly.common.aws.ses.SesService;
import com.nidus.twinly.common.domain.VerificationType;
import com.nidus.twinly.common.solapi.SolapiService;
import com.solapi.sdk.message.exception.SolapiEmptyResponseException;
import com.solapi.sdk.message.exception.SolapiMessageNotReceivedException;
import com.solapi.sdk.message.exception.SolapiUnknownException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int CODE_EXPIRES_MINUTES = 5;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int VERIFIED_TOKEN_EXPIRES_MINUTES = 30;

    private final VerificationSessionRepository verificationSessionRepository;
    private final SesService sesService;
    private final SolapiService solapiService;

    @Transactional
    public AuthEmailSendResult emailSend(AuthEmailSendCommand command) {
        if (command.email() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
        }

        String code = generateCode();
        Instant codeExpiresAt = Instant.now().plus(CODE_EXPIRES_MINUTES, ChronoUnit.MINUTES);

        VerificationSession session = VerificationSession.create(VerificationType.EMAIL, command.email(), code, codeExpiresAt);
        verificationSessionRepository.save(session);

        sesService.send(
                command.email(),
                "[트윈리] 인증번호 발송",
                "인증번호는 [%s] 입니다. %d분 이내에 입력해주세요.".formatted(code, CODE_EXPIRES_MINUTES)
        );

        return new AuthEmailSendResult(session.getVerificationToken(), codeExpiresAt);
    }

    private String generateCode() {
        int number = RANDOM.nextInt(1_000_000);
        return String.format("%06d", number);
    }

    @Transactional
    public AuthEmailVerifyResult emailVerify(AuthEmailVerifyCommand command) {
        if (command.code() == null || command.emailVerificationToken() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
        }

        VerificationSession session = verificationSessionRepository.findByVerificationToken(command.emailVerificationToken())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "유효하지 않은 인증 요청입니다."));

        if (session.getCodeExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "인증번호가 만료되었습니다.");
        }

        if (!session.getCode().equals(command.code())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "인증번호가 일치하지 않습니다.");
        }

        Instant verifiedTokenExpiresAt = Instant.now().plus(VERIFIED_TOKEN_EXPIRES_MINUTES, ChronoUnit.MINUTES);
        session.verify(verifiedTokenExpiresAt);

        return new AuthEmailVerifyResult(session.getVerifiedToken());
    }

    @Transactional
    public AuthSmsSendResult smsSend(AuthSmsSendCommand command) {
        if (command.phone() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
        }

        String code = generateCode();
        Instant codeExpiresAt = Instant.now().plus(CODE_EXPIRES_MINUTES, ChronoUnit.MINUTES);

        VerificationSession session = VerificationSession.create(VerificationType.SMS, command.phone(), code, codeExpiresAt);
        verificationSessionRepository.save(session);

        try {
            solapiService.send(
                    command.phone(),
                    "[트윈리] 인증번호는 [%s] 입니다. %d분 이내에 입력해주세요.".formatted(code, CODE_EXPIRES_MINUTES)
            );
        } catch (SolapiEmptyResponseException | SolapiMessageNotReceivedException | SolapiUnknownException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SMS 발송에 실패했습니다.", e);
        }

        return new AuthSmsSendResult(session.getVerificationToken(), codeExpiresAt);
    }
}
