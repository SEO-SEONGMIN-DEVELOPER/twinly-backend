package com.nidus.twinly.auth.service;

import com.nidus.twinly.anon.dto.snapshot.AnonSessionSnapshot;
import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.anon.entity.AnonSessionAgreement;
import com.nidus.twinly.anon.entity.AnonSessionPersonaElement;
import com.nidus.twinly.anon.entity.AnonSessionPhoto;
import com.nidus.twinly.anon.repository.AnonSessionAgreementRepository;
import com.nidus.twinly.anon.repository.AnonSessionPersonaElementRepository;
import com.nidus.twinly.anon.repository.AnonSessionPhotoRepository;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import com.nidus.twinly.auth.entity.RefreshToken;
import com.nidus.twinly.auth.repository.RefreshTokenRepository;
import com.nidus.twinly.legal.entity.Agreement;
import com.nidus.twinly.legal.repository.AgreementRepository;
import com.nidus.twinly.auth.dto.command.*;
import com.nidus.twinly.auth.dto.result.*;
import com.nidus.twinly.auth.entity.AnonSessionVerificationSession;
import com.nidus.twinly.auth.entity.VerificationSession;
import com.nidus.twinly.auth.repository.AnonSessionVerificationSessionRepository;
import com.nidus.twinly.auth.repository.VerificationSessionRepository;
import com.nidus.twinly.common.crypto.BlindIndexHasher;
import com.nidus.twinly.common.domain.VerificationType;
import com.nidus.twinly.common.jwt.JwtService;
import com.nidus.twinly.common.photo.ProfileThumbnailService;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.school.entity.School;
import com.nidus.twinly.school.service.SchoolCatalog;
import com.nidus.twinly.user.entity.PersonaElement;
import com.nidus.twinly.user.entity.Photo;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.entity.Verification;
import com.nidus.twinly.user.repository.PersonaElementRepository;
import com.nidus.twinly.user.repository.PhotoRepository;
import com.nidus.twinly.user.repository.UserRepository;
import com.nidus.twinly.user.repository.VerificationRepository;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final VerificationCodeIssuer verificationCodeIssuer;
    private final JwtService jwtService;
    private final VerificationService verificationService;
    private final SchoolCatalog schoolCatalog;

    private final VerificationSessionRepository verificationSessionRepository;
    private final AnonSessionVerificationSessionRepository anonSessionVerificationSessionRepository;
    private final AnonSessionRepository anonSessionRepository;
    private final UserRepository userRepository;
    private final AnonSessionPhotoRepository anonSessionPhotoRepository;
    private final AnonSessionAgreementRepository anonSessionAgreementRepository;
    private final AnonSessionPersonaElementRepository anonSessionPersonaElementRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AgreementRepository agreementRepository;
    private final PhotoRepository photoRepository;
    private final PersonaElementRepository personaElementRepository;
    private final VerificationRepository verificationRepository;

    private final BlindIndexHasher blindIndexHasher;
    private final ProfileThumbnailService profileThumbnailService;

    @Transactional
    public AuthEmailSendResult onboardingEmailSend(AnonSessionSnapshot anonSessionSnapshot, AuthEmailSendCommand command) {
        schoolCatalog.requireSupportedDomain(command.email());

        String code = verificationCodeIssuer.issue(command.email());
        Instant codeExpiresAt = verificationCodeIssuer.codeExpiresAt();

        AnonSessionVerificationSession session = upsertVerificationSession(
                anonSessionSnapshot.id(), VerificationType.EMAIL, command.email(), code, codeExpiresAt);

        verificationCodeIssuer.send(VerificationType.EMAIL, command.email(), code);

        return new AuthEmailSendResult(session.getVerificationToken(), codeExpiresAt);
    }

    @Transactional
    public void onboardingEmailVerify(AnonSessionSnapshot anonSessionSnapshot, AuthEmailVerifyCommand command) {
        Long anonSessionId = anonSessionSnapshot.id();

        AnonSessionVerificationSession session = verifyAnonSession(anonSessionId, command, VerificationType.EMAIL);
        School school = schoolCatalog.findByEmail(session.getContact());

        AnonSession anonSession = anonSessionRepository.findById(anonSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ANON_SESSION));

        anonSession.changeSchool(school.getName());
    }

    @Transactional
    public AuthSmsSendResult onboardingSmsSend(AnonSessionSnapshot anonSessionSnapshot, AuthSmsSendCommand command) {
        String code = verificationCodeIssuer.issue(command.phone());
        Instant codeExpiresAt = verificationCodeIssuer.codeExpiresAt();

        AnonSessionVerificationSession session = upsertVerificationSession(
                anonSessionSnapshot.id(), VerificationType.SMS, command.phone(), code, codeExpiresAt);

        verificationCodeIssuer.send(VerificationType.SMS, command.phone(), code);

        return new AuthSmsSendResult(session.getVerificationToken(), codeExpiresAt);
    }

    @Transactional
    public void onboardingSmsVerify(AnonSessionSnapshot anonSessionSnapshot, AuthSmsVerifyCommand command) {
        verifyAnonSession(anonSessionSnapshot.id(), command, VerificationType.SMS);
    }

    @Transactional
    public AuthEmailSendResult emailSend(AuthEmailSendCommand command) {
        if (!userRepository.existsByEmailHash(blindIndexHasher.hash(command.email()))) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_REGISTERED);
        }

        String code = verificationCodeIssuer.issue(command.email());
        Instant codeExpiresAt = verificationCodeIssuer.codeExpiresAt();

        VerificationSession session = VerificationSession.create(VerificationType.EMAIL, command.email(), code, codeExpiresAt);
        verificationSessionRepository.save(session);

        verificationCodeIssuer.send(VerificationType.EMAIL, command.email(), code);

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
            throw new BusinessException(ErrorCode.PHONE_NOT_REGISTERED);
        }

        String code = verificationCodeIssuer.issue(command.phone());
        Instant codeExpiresAt = verificationCodeIssuer.codeExpiresAt();

        VerificationSession session = VerificationSession.create(VerificationType.SMS, command.phone(), code, codeExpiresAt);
        verificationSessionRepository.save(session);

        verificationCodeIssuer.send(VerificationType.SMS, command.phone(), code);

        return new AuthSmsSendResult(session.getVerificationToken(), codeExpiresAt);
    }

    @Transactional
    public AuthSmsVerifyResult smsVerify(AuthSmsVerifyCommand command) {
        VerificationSession session = verificationService.verify(command, VerificationType.SMS);

        return new AuthSmsVerifyResult(session.getVerifiedToken(), session.getVerifiedTokenExpiresAt());
    }

    private AnonSessionVerificationSession upsertVerificationSession(
            Long anonSessionId, VerificationType type, String contact, String code, Instant codeExpiresAt) {
        AnonSessionVerificationSession session = anonSessionVerificationSessionRepository
                .findByAnonSessionIdAndType(anonSessionId, type)
                .orElse(null);

        if (session == null) {
            session = AnonSessionVerificationSession.create(type, anonSessionId, contact, code, codeExpiresAt);
            anonSessionVerificationSessionRepository.save(session);
        } else {
            session.refresh(contact, code, codeExpiresAt);
        }

        return session;
    }

    private AnonSessionVerificationSession verifyAnonSession(Long anonSessionId, VerifyCommand command, VerificationType type) {
        AnonSessionVerificationSession session = anonSessionVerificationSessionRepository
                .findByAnonSessionIdAndType(anonSessionId, type)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND));

        if (!session.getVerificationToken().equals(command.verificationToken())) {
            throw new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND);
        }
        if (session.getCodeExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_EXPIRED);
        }
        if (!session.getCode().equals(command.value())) {
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_MISMATCH);
        }

        session.verify();

        return session;
    }

    @Transactional
    public AuthTokenResult signup(AnonSessionSnapshot anonSessionSnapshot) {
        Long anonSessionId = anonSessionSnapshot.id();
        AnonSessionVerificationSession smsSession = requireVerified(anonSessionId, VerificationType.SMS);
        AnonSessionVerificationSession emailSession = requireVerified(anonSessionId, VerificationType.EMAIL);

        AnonSession anonSession = anonSessionRepository.findById(anonSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SIGNUP_SESSION_NOT_FOUND));

        requireProfileCompleted(anonSession);

        String phoneNumber = smsSession.getContact();
        String phoneNumberHash = blindIndexHasher.hash(phoneNumber);
        String email = emailSession.getContact();
        String emailHash = blindIndexHasher.hash(email);

        if (userRepository.existsByPhoneNumberHash(phoneNumberHash)) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_REGISTERED);
        }

        if (userRepository.existsByEmailHash(emailHash)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        String familyNameHash = blindIndexHasher.hash(anonSession.getFamilyName());
        String givenNameHash = blindIndexHasher.hash(anonSession.getGivenName());
        String schoolHash = blindIndexHasher.hash(anonSession.getSchool());
        String affiliationHash = blindIndexHasher.hash(anonSession.getAffiliation());
        String affiliationNumberHash = blindIndexHasher.hash(anonSession.getAffiliationNumber());
        String birthDateHash = blindIndexHasher.hash(anonSession.getBirthDate());

        User user = userRepository.save(
                User.create(
                        anonSession.getNickname(),
                        anonSession.getFamilyName(), familyNameHash,
                        anonSession.getGivenName(), givenNameHash,
                        anonSession.getGender(),
                        anonSession.getSchool(), schoolHash,
                        anonSession.getAffiliation(), affiliationHash,
                        anonSession.getAffiliationNumber(), affiliationNumberHash,
                        anonSession.getBirthDate(), birthDateHash,
                        phoneNumber, phoneNumberHash,
                        email, emailHash
                )
        );

        anonSessionVerificationSessionRepository.deleteByAnonSessionId(anonSessionId);

        List<AnonSessionPhoto> anonSessionPhotos = anonSessionPhotoRepository.findAllByAnonSessionId(anonSessionId);

        anonSessionPhotos.forEach(anonSessionPhoto -> {
            Photo photo = Photo.create(
                    user.getId(),
                    anonSessionPhoto.getType(),
                    anonSessionPhoto.getKey(),
                    anonSessionPhoto.getXPos(),
                    anonSessionPhoto.getYPos(),
                    anonSessionPhoto.getWidth(),
                    anonSessionPhoto.getHeight(),
                    anonSessionPhoto.getUploadedAt()
            );
            photo.changeThumbnailKey(
                    profileThumbnailService.generate(anonSessionPhoto.getKey(), anonSessionPhoto.position()));

            photoRepository.save(photo);
        });

        anonSessionPhotoRepository.deleteAll(anonSessionPhotos);

        List<AnonSessionAgreement> anonSessionAgreements = anonSessionAgreementRepository.findAllByAnonSessionId(anonSessionId);

        anonSessionAgreements.stream()
                .filter(anonSessionAgreement -> anonSessionAgreement.getRevokedAt() == null)
                .forEach(anonSessionAgreement -> agreementRepository.save(
                        Agreement.create(
                                user.getId(),
                                anonSessionAgreement.getPolicyId(),
                                anonSessionAgreement.getAgreedAt()
                        )
                ));

        anonSessionAgreementRepository.deleteAll(anonSessionAgreements);

        List<AnonSessionPersonaElement> anonSessionPersonaElements = anonSessionPersonaElementRepository.findAllByAnonSessionId(anonSessionId);

        anonSessionPersonaElements.forEach(anonSessionPersonaElement -> personaElementRepository.save(
                PersonaElement.create(
                        user.getId(),
                        anonSessionPersonaElement.getDimension(),
                        anonSessionPersonaElement.getExplanation(),
                        anonSessionPersonaElement.getCreatedAt()
                )
        ));

        anonSessionPersonaElementRepository.deleteAll(anonSessionPersonaElements);

        anonSessionRepository.delete(anonSession);

        verificationRepository.save(Verification.create(user.getId(), VerificationType.SMS, smsSession.getVerifiedAt()));
        verificationRepository.save(Verification.create(user.getId(), VerificationType.EMAIL, emailSession.getVerifiedAt()));

        return issueAuthToken(user.getId());
    }

    private VerificationSession verifySession(VerificationType type, UUID verifiedToken) {
        VerificationSession session = verificationSessionRepository.findByTypeAndVerifiedToken(type, verifiedToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND));

        if (session.getVerifiedTokenExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.VERIFICATION_EXPIRED);
        }

        return session;
    }

    private AnonSessionVerificationSession requireVerified(Long anonSessionId, VerificationType type) {
        return anonSessionVerificationSessionRepository.findByAnonSessionIdAndType(anonSessionId, type)
                .filter(session -> session.getVerifiedAt() != null)
                .orElseThrow(() -> new BusinessException(verificationNotCompletedCode(type)));
    }

    private ErrorCode verificationNotCompletedCode(VerificationType type) {
        return type == VerificationType.SMS
                ? ErrorCode.SMS_VERIFICATION_NOT_COMPLETED
                : ErrorCode.EMAIL_VERIFICATION_NOT_COMPLETED;
    }

    private void requireProfileCompleted(AnonSession anonSession) {
        if (anonSession.getNickname() == null
                || anonSession.getFamilyName() == null
                || anonSession.getGivenName() == null
                || anonSession.getGender() == null
                || anonSession.getSchool() == null
                || anonSession.getAffiliation() == null
                || anonSession.getAffiliationNumber() == null
                || anonSession.getBirthDate() == null) {
            throw new BusinessException(ErrorCode.PROFILE_NOT_COMPLETED);
        }
    }

    @Transactional
    public AuthTokenResult login(AuthLoginCommand command) {
        VerificationSession smsSession = verifySession(VerificationType.SMS, command.smsVerifiedToken());

        String phoneNumberHash = blindIndexHasher.hash(smsSession.getContact());

        User user = userRepository.findByPhoneNumberHash(phoneNumberHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.PHONE_NOT_REGISTERED));

        return issueAuthToken(user.getId());
    }

    @Transactional
    public AuthTokenResult refresh(AuthRefreshCommand command) {
        Long userId;
        try {
            userId = jwtService.parseRefreshTokenUserId(command.refreshToken());
        } catch (JwtException e) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, e);
        }

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(blindIndexHasher.hash(command.refreshToken()))
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_ALREADY_REVOKED));

        if (!refreshToken.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        refreshTokenRepository.delete(refreshToken);

        return issueAuthToken(userId);
    }

    @Transactional
    public void logout(AuthLogoutCommand command) {
        refreshTokenRepository.deleteByTokenHash(blindIndexHasher.hash(command.refreshToken()));
    }

    private AuthTokenResult issueAuthToken(Long userId) {
        AuthTokenResult tokens = jwtService.generateAuthTokenResult(userId);

        refreshTokenRepository.save(RefreshToken.create(userId, blindIndexHasher.hash(tokens.refreshToken()), tokens.refreshExpiresAt()));

        return tokens;
    }
}