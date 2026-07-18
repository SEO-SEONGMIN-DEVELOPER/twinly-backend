package com.nidus.twinly.auth.service;

import com.nidus.twinly.anon.dto.snapshot.AnonSessionSnapshot;
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
import com.nidus.twinly.common.jwt.JwtService;
import com.nidus.twinly.common.solapi.SolapiService;
import com.nidus.twinly.user.entity.Photo;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.entity.Verification;
import com.nidus.twinly.user.repository.PhotoRepository;
import com.nidus.twinly.user.repository.UserRepository;
import com.nidus.twinly.user.repository.VerificationRepository;
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

/* [멘토링 피드백 반영 완료]
 * 1. verify 로직에 대해서 중복 제거 및 책임 분리
 * 2. 온보딩 / 로그인 API 분리
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int CODE_EXPIRES_MINUTES = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SesService sesService;
    private final SolapiService solapiService;
    private final JwtService jwtService;
    private final VerificationService verificationService;

    private final VerificationSessionRepository verificationSessionRepository;
    private final AnonSessionRepository anonSessionRepository;
    private final UserRepository userRepository;
    private final AnonSessionPhotoRepository anonSessionPhotoRepository;
    private final PhotoRepository photoRepository;
    private final VerificationRepository verificationRepository;

    private final BlindIndexHasher blindIndexHasher;

    @Transactional
    public AuthEmailSendResult onboardingEmailSend(AuthEmailSendCommand command) {
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

    @Transactional
    public AuthEmailVerifyResult onboardingEmailVerify(AuthEmailVerifyCommand command) {
        VerificationSession session = verificationService.verify(command, VerificationType.EMAIL);

        return new AuthEmailVerifyResult(session.getVerifiedToken(), session.getVerifiedTokenExpiresAt());
    }

    @Transactional
    public AuthSmsSendResult onboardingSmsSend(AuthSmsSendCommand command) {
        String code = generateCode();
        Instant codeExpiresAt = Instant.now().plus(CODE_EXPIRES_MINUTES, ChronoUnit.MINUTES);

        VerificationSession session = VerificationSession.create(VerificationType.SMS, command.phone(), code, codeExpiresAt);
        verificationSessionRepository.save(session);

        solapiService.send(
                command.phone(),
                "[트윈리] 인증번호는 [%s] 입니다. %d분 이내에 입력해주세요.".formatted(code, CODE_EXPIRES_MINUTES)
        );

        return new AuthSmsSendResult(session.getVerificationToken(), codeExpiresAt);
    }

    @Transactional
    public AuthSmsVerifyResult onboardingSmsVerify(AuthSmsVerifyCommand command) {
        VerificationSession session = verificationService.verify(command, VerificationType.SMS);

        return new AuthSmsVerifyResult(session.getVerifiedToken(), session.getVerifiedTokenExpiresAt());
    }

    @Transactional
    public AuthEmailSendResult emailSend(AuthEmailSendCommand command) {
        if (!userRepository.existsByEmailHash(blindIndexHasher.hash(command.email()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "가입되지 않은 이메일입니다.");
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

    @Transactional
    public AuthEmailVerifyResult emailVerify(AuthEmailVerifyCommand command) {
        VerificationSession session = verificationService.verify(command, VerificationType.EMAIL);

        return new AuthEmailVerifyResult(session.getVerifiedToken(), session.getVerifiedTokenExpiresAt());
    }

    @Transactional
    public AuthSmsSendResult smsSend(AuthSmsSendCommand command) {
        if (!userRepository.existsByPhoneNumberHash(blindIndexHasher.hash(command.phone()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "가입되지 않은 전화번호입니다.");
        }

        String code = generateCode();
        Instant codeExpiresAt = Instant.now().plus(CODE_EXPIRES_MINUTES, ChronoUnit.MINUTES);

        VerificationSession session = VerificationSession.create(VerificationType.SMS, command.phone(), code, codeExpiresAt);
        verificationSessionRepository.save(session);

        solapiService.send(
                command.phone(),
                "[트윈리] 인증번호는 [%s] 입니다. %d분 이내에 입력해주세요.".formatted(code, CODE_EXPIRES_MINUTES)
        );

        return new AuthSmsSendResult(session.getVerificationToken(), codeExpiresAt);
    }

    @Transactional
    public AuthSmsVerifyResult smsVerify(AuthSmsVerifyCommand command) {
        VerificationSession session = verificationService.verify(command, VerificationType.SMS);

        return new AuthSmsVerifyResult(session.getVerifiedToken(), session.getVerifiedTokenExpiresAt());
    }

    private String generateCode() {
        int number = RANDOM.nextInt(1_000_000);
        return String.format("%06d", number);
    }

    @Transactional
    public AuthTokenResult signup(AnonSessionSnapshot anonSessionSnapshot, AuthSignupCommand command) {
        Long anonSessionId = anonSessionSnapshot.id();
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

        String familyNameHash = blindIndexHasher.hash(anonSession.getFamilyName());
        String givenNameHash = blindIndexHasher.hash(anonSession.getGivenName());
        String affiliationHash = blindIndexHasher.hash(anonSession.getAffiliation());
        String affiliationNumberHash = blindIndexHasher.hash(anonSession.getAffiliationNumber());
        String birthDateHash = blindIndexHasher.hash(anonSession.getBirthDate());

        User user = userRepository.save(
                User.create(
                        anonSession.getNickname(),
                        anonSession.getFamilyName(), familyNameHash,
                        anonSession.getGivenName(), givenNameHash,
                        anonSession.getGender(),
                        anonSession.getAffiliation(), affiliationHash,
                        anonSession.getAffiliationNumber(), affiliationNumberHash,
                        anonSession.getBirthDate(), birthDateHash,
                        phoneNumber, phoneNumberHash,
                        email, emailHash
                )
        );

        anonSessionRepository.delete(anonSession);

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

        return jwtService.generateAuthTokenResult(user.getId());
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
    public AuthTokenResult login(AuthLoginCommand command) {
        VerificationSession smsSession = verifySession(VerificationType.SMS, command.smsVerifiedToken());

        String phoneNumberHash = blindIndexHasher.hash(smsSession.getContact());

        User user = userRepository.findByPhoneNumberHash(phoneNumberHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "가입되지 않은 전화번호입니다."));

        return jwtService.generateAuthTokenResult(user.getId());
    }

    public AuthTokenResult refresh(AuthRefreshCommand command) {
        Long userId;
        try {
            userId = jwtService.parseRefreshTokenUserId(command.refreshToken());
        } catch (JwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다.", e);
        }

        return jwtService.generateAuthTokenResult(userId);
    }
}