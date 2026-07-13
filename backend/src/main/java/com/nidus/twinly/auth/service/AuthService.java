package com.nidus.twinly.auth.service;

import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.anon.entity.AnonSessionPhoto;
import com.nidus.twinly.anon.repository.AnonSessionPhotoRepository;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import com.nidus.twinly.auth.dto.command.*;
import com.nidus.twinly.auth.dto.result.*;
import com.nidus.twinly.auth.entity.VerificationSession;
import com.nidus.twinly.auth.repository.VerificationSessionRepository;
import com.nidus.twinly.common.aws.ses.SesService;
import com.nidus.twinly.common.crypto.BlindIndexHasher;
import com.nidus.twinly.common.domain.VerificationType;
import com.nidus.twinly.common.jwt.Jwt;
import com.nidus.twinly.common.jwt.JwtService;
import com.nidus.twinly.common.solapi.SolapiService;
import com.nidus.twinly.user.entity.Photo;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.entity.Verification;
import com.nidus.twinly.user.repository.PhotoRepository;
import com.nidus.twinly.user.repository.UserRepository;
import com.nidus.twinly.user.repository.VerificationRepository;
import com.solapi.sdk.message.exception.SolapiEmptyResponseException;
import com.solapi.sdk.message.exception.SolapiMessageNotReceivedException;
import com.solapi.sdk.message.exception.SolapiUnknownException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int CODE_EXPIRES_MINUTES = 5;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int VERIFIED_TOKEN_EXPIRES_MINUTES = 30;

    private final SesService sesService;
    private final SolapiService solapiService;
    private final JwtService jwtService;

    private final VerificationSessionRepository verificationSessionRepository;
    private final AnonSessionRepository anonSessionRepository;
    private final UserRepository userRepository;
    private final AnonSessionPhotoRepository anonSessionPhotoRepository;
    private final PhotoRepository photoRepository;
    private final VerificationRepository verificationRepository;

    private final BlindIndexHasher blindIndexHasher;

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

        VerificationSession session = verificationSessionRepository.findByTypeAndVerificationToken(VerificationType.EMAIL, command.emailVerificationToken())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "유효하지 않은 인증 요청입니다."));

        if (session.getCodeExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "인증번호가 만료되었습니다.");
        }

        if (!session.getCode().equals(command.code())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "인증번호가 일치하지 않습니다.");
        }

        Instant verifiedTokenExpiresAt = Instant.now().plus(VERIFIED_TOKEN_EXPIRES_MINUTES, ChronoUnit.MINUTES);
        session.verify(verifiedTokenExpiresAt);

        return new AuthEmailVerifyResult(session.getVerifiedToken(), session.getVerifiedTokenExpiresAt());
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

    @Transactional
    public AuthSmsVerifyResult smsVerify(AuthSmsVerifyCommand command) {
        if (command.code() == null || command.smsVerificationToken() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
        }

        VerificationSession session = verificationSessionRepository.findByTypeAndVerificationToken(VerificationType.SMS, command.smsVerificationToken())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "유효하지 않은 인증 요청입니다."));

        if (session.getCodeExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "인증번호가 만료되었습니다.");
        }

        if (!session.getCode().equals(command.code())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "인증번호가 일치하지 않습니다.");
        }

        Instant verifiedTokenExpiresAt = Instant.now().plus(VERIFIED_TOKEN_EXPIRES_MINUTES, ChronoUnit.MINUTES);
        session.verify(verifiedTokenExpiresAt);

        return new AuthSmsVerifyResult(session.getVerifiedToken(), session.getVerifiedTokenExpiresAt());
    }

    @Transactional
    public AuthSignupResult signup(Long anonSessionId, AuthSignupCommand command) {
        if (command.verifiedToken() == null || command.verifiedToken().smsVerifiedToken() == null || command.verifiedToken().emailVerifiedToken() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
        }

        VerificationSession smsSession = verifySession(VerificationType.SMS, command.verifiedToken().smsVerifiedToken());
        VerificationSession emailSession = verifySession( VerificationType.EMAIL, command.verifiedToken().emailVerifiedToken());

        AnonSession anonSession = anonSessionRepository.findById(anonSessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "유효하지 않은 세션입니다."));

        String phoneNumber = smsSession.getContact();
        String phoneNumberHash = blindIndexHasher.hash(phoneNumber);
        String email = emailSession.getContact();
        String emailHash = blindIndexHasher.hash(email);

        if (userRepository.existsByPhoneNumberHash(phoneNumberHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 전화번호입니다.");
        }

        if (userRepository.existsByEmailHash(emailHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다.");
        }

        User user = userRepository.save(
                User.create(
                        anonSession.getNickname(),
                        anonSession.getFamilyName(),
                        anonSession.getGivenName(),
                        anonSession.getGender(),
                        anonSession.getAffiliation(),
                        anonSession.getAffiliationNumber(),
                        anonSession.getBirthDate(),
                        anonSession.getHeight(),
                        phoneNumber,
                        phoneNumberHash,
                        email,
                        emailHash
                )
        );

        List<AnonSessionPhoto> anonSessionPhotos = anonSessionPhotoRepository.findAllByAnonSessionId(anonSessionId);

        anonSessionPhotos.forEach(anonSessionPhoto -> photoRepository.save(
                        Photo.create(
                                user.getId(),
                                anonSessionPhoto.getType(),
                                anonSessionPhoto.getKey(),
                                anonSessionPhoto.getXPos(),
                                anonSessionPhoto.getYPos(),
                                anonSessionPhoto.getWidth(),
                                anonSessionPhoto.getHeight(),
                                anonSessionPhoto.getUploadedAt()
                        )
                ));

        anonSessionPhotoRepository.deleteAll(anonSessionPhotos);

        verificationRepository.save(Verification.create(user.getId(), VerificationType.SMS, smsSession.getVerifiedAt()));
        verificationRepository.save(Verification.create(user.getId(), VerificationType.EMAIL, emailSession.getVerifiedAt()));

        verificationSessionRepository.delete(smsSession);
        verificationSessionRepository.delete(emailSession);

        Jwt accessToken = jwtService.generateAccessToken(user.getId());
        Jwt refreshToken = jwtService.generateRefreshToken(user.getId());

        return new AuthSignupResult(accessToken.value(), accessToken.expiresAt(), refreshToken.value(), refreshToken.expiresAt());
    }

    private VerificationSession verifySession(VerificationType type, UUID verifiedToken) {
        VerificationSession session = verificationSessionRepository.findByTypeAndVerifiedToken(type, verifiedToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "유효하지 않은 인증 요청입니다."));

        if (session.getVerifiedTokenExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "인증이 만료되었습니다.");
        }

        return session;
    }

    @Transactional
    public AuthLoginResult login(AuthLoginCommand command) {
        if (command.smsVerifiedToken() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
        }

        VerificationSession smsSession = verifySession(VerificationType.SMS, command.smsVerifiedToken());

        String phoneNumberHash = blindIndexHasher.hash(smsSession.getContact());

        User user = userRepository.findByPhoneNumberHash(phoneNumberHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "가입되지 않은 전화번호입니다."));

        verificationSessionRepository.delete(smsSession);

        Jwt accessToken = jwtService.generateAccessToken(user.getId());
        Jwt refreshToken = jwtService.generateRefreshToken(user.getId());

        return new AuthLoginResult(accessToken.value(), accessToken.expiresAt(), refreshToken.value(), refreshToken.expiresAt());
    }

    public AuthRefreshResult refresh(AuthRefreshCommand command) {
        if (command.refreshToken() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
        }

        Long userId;
        try {
            userId = jwtService.parseRefreshTokenUserId(command.refreshToken());
        } catch (JwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다.", e);
        }

        Jwt accessToken = jwtService.generateAccessToken(userId);
        Jwt refreshToken = jwtService.generateRefreshToken(userId);

        return new AuthRefreshResult(accessToken.value(), accessToken.expiresAt(), refreshToken.value(), refreshToken.expiresAt());
    }
}
