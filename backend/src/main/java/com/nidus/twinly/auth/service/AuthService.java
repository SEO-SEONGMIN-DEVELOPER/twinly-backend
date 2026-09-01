package com.nidus.twinly.auth.service;

import com.nidus.twinly.aichat.entity.AiChat;
import com.nidus.twinly.aichat.entity.AnonSessionAiChat;
import com.nidus.twinly.aichat.repository.AiChatRepository;
import com.nidus.twinly.aichat.repository.AnonSessionAiChatRepository;
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
import com.nidus.twinly.auth.client.PortOneIdentityClient;
import com.nidus.twinly.auth.client.PortOneIdentityVerificationBody;
import com.nidus.twinly.auth.client.PortOneIdentityVerificationStatus;
import com.nidus.twinly.auth.config.PortOneProperties;
import com.nidus.twinly.auth.dto.command.*;
import com.nidus.twinly.auth.dto.result.*;
import com.nidus.twinly.auth.entity.AnonSessionIdentityVerification;
import com.nidus.twinly.auth.entity.AnonSessionVerificationSession;
import com.nidus.twinly.auth.entity.VerificationSession;
import com.nidus.twinly.auth.repository.AnonSessionIdentityVerificationRepository;
import com.nidus.twinly.auth.repository.AnonSessionVerificationSessionRepository;
import com.nidus.twinly.auth.repository.VerificationSessionRepository;
import com.nidus.twinly.common.crypto.BlindIndexHasher;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.domain.VerificationType;
import com.nidus.twinly.common.jwt.JwtService;
import com.nidus.twinly.common.photo.ProfileThumbnailService;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.onboarding.repository.SurveyAnswerRepository;
import com.nidus.twinly.organization.entity.Organization;
import com.nidus.twinly.organization.service.OrganizationCatalog;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String IDENTITY_VERIFICATION_ID_PREFIX = "identity-";
    private static final int IDENTITY_EXPIRES_MINUTES = 30;
    private static final Duration IDENTITY_ISSUE_WINDOW = Duration.ofHours(1);
    private static final int IDENTITY_ISSUE_LIMIT = 5;
    private static final int IDENTITY_MIN_AGE = 18;
    private static final int IDENTITY_MAX_AGE = 29;

    private final VerificationCodeIssuer verificationCodeIssuer;
    private final JwtService jwtService;
    private final VerificationService verificationService;
    private final OrganizationCatalog organizationCatalog;
    private final PortOneIdentityClient portOneIdentityClient;
    private final PortOneProperties portOneProperties;

    private final VerificationSessionRepository verificationSessionRepository;
    private final AnonSessionVerificationSessionRepository anonSessionVerificationSessionRepository;
    private final AnonSessionIdentityVerificationRepository anonSessionIdentityVerificationRepository;
    private final AnonSessionRepository anonSessionRepository;
    private final UserRepository userRepository;
    private final AnonSessionPhotoRepository anonSessionPhotoRepository;
    private final AnonSessionAgreementRepository anonSessionAgreementRepository;
    private final AnonSessionPersonaElementRepository anonSessionPersonaElementRepository;
    private final AnonSessionAiChatRepository anonSessionAiChatRepository;
    private final AiChatRepository aiChatRepository;
    private final SurveyAnswerRepository surveyAnswerRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AgreementRepository agreementRepository;
    private final PhotoRepository photoRepository;
    private final PersonaElementRepository personaElementRepository;
    private final VerificationRepository verificationRepository;

    private final BlindIndexHasher blindIndexHasher;
    private final ProfileThumbnailService profileThumbnailService;

    @Transactional
    public AuthEmailSendResult onboardingEmailSend(AnonSessionSnapshot anonSessionSnapshot, AuthEmailSendCommand command) {
        organizationCatalog.requireSupportedDomain(command.email());

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
        Organization organization = organizationCatalog.findByEmail(session.getContact());

        AnonSession anonSession = anonSessionRepository.findById(anonSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ANON_SESSION));

        anonSession.changeOrganization(organization.getName());
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
    public AuthIdentityPrepareResult onboardingIdentityPrepare(AnonSessionSnapshot anonSessionSnapshot) {
        Instant now = Instant.now();
        String identityVerificationId = IDENTITY_VERIFICATION_ID_PREFIX + UUID.randomUUID();
        Instant expiresAt = now.plus(IDENTITY_EXPIRES_MINUTES, ChronoUnit.MINUTES);

        AnonSessionIdentityVerification verification = anonSessionIdentityVerificationRepository
                .findByAnonSessionId(anonSessionSnapshot.id())
                .orElse(null);

        if (verification == null) {
            anonSessionIdentityVerificationRepository.save(
                    AnonSessionIdentityVerification.create(anonSessionSnapshot.id(), identityVerificationId, expiresAt));

            return new AuthIdentityPrepareResult(identityVerificationId, expiresAt);
        }

        if (verification.isVerified()) {
            throw new BusinessException(ErrorCode.IDENTITY_ALREADY_VERIFIED);
        }

        if (verification.isRateLimited(now, IDENTITY_ISSUE_WINDOW, IDENTITY_ISSUE_LIMIT)) {
            throw new BusinessException(ErrorCode.IDENTITY_RATE_LIMITED);
        }

        verification.countIssue(now, IDENTITY_ISSUE_WINDOW);
        verification.refresh(identityVerificationId, expiresAt);

        return new AuthIdentityPrepareResult(identityVerificationId, expiresAt);
    }

    @Transactional
    public void onboardingIdentityVerify(AnonSessionSnapshot anonSessionSnapshot) {
        Long anonSessionId = anonSessionSnapshot.id();

        AnonSessionIdentityVerification verification = anonSessionIdentityVerificationRepository
                .findByAnonSessionId(anonSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTITY_NOT_VERIFIED));

        if (verification.isVerified()) {
            return;
        }

        if (verification.isExpired(Instant.now())) {
            throw new BusinessException(ErrorCode.IDENTITY_NOT_VERIFIED);
        }

        PortOneIdentityVerificationBody body = portOneIdentityClient
                .identityVerification(verification.getIdentityVerificationId())
                .orElse(null);

        if (body == null) {
            log.info("본인인증 건을 PortOne에서 찾을 수 없습니다. anonSessionId={}, status=NOT_FOUND", anonSessionId);
            throw new BusinessException(ErrorCode.IDENTITY_NOT_VERIFIED);
        }

        if (body.status() != PortOneIdentityVerificationStatus.VERIFIED) {
            log.info("본인인증이 완료되지 않은 건입니다. anonSessionId={}, status={}", anonSessionId, body.status());
            throw new BusinessException(ErrorCode.IDENTITY_NOT_VERIFIED);
        }

        if (body.channel() == null || body.verifiedCustomer() == null) {
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_FAILED);
        }

        if (!portOneProperties.allows(body.channel().type())) {
            log.warn("허용되지 않은 채널의 본인인증 건입니다. anonSessionId={}, channelType={}", anonSessionId, body.channel().type());
            throw new BusinessException(ErrorCode.IDENTITY_NOT_VERIFIED);
        }

        PortOneIdentityVerificationBody.VerifiedCustomer customer = body.verifiedCustomer();
        Gender gender = toGender(customer.gender());

        if (isBlank(customer.name()) || gender == null || isBlank(customer.phoneNumber()) || isBlank(customer.ci())) {
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_FAILED);
        }

        LocalDate birthDate = parseBirthDate(customer.birthDate());

        if (!isAllowedAge(birthDate)) {
            throw new BusinessException(ErrorCode.IDENTITY_AGE_NOT_ALLOWED);
        }

        String ciHash = blindIndexHasher.hash(customer.ci());

        if (userRepository.existsByCiHash(ciHash)) {
            throw new BusinessException(ErrorCode.IDENTITY_ALREADY_REGISTERED);
        }

        verification.verify(
                customer.name(),
                birthDate.toString(),
                gender,
                customer.phoneNumber(),
                customer.ci(),
                ciHash
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private LocalDate parseBirthDate(String birthDate) {
        if (birthDate == null) {
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_FAILED);
        }

        try {
            return LocalDate.parse(birthDate);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_FAILED, e);
        }
    }

    private boolean isAllowedAge(LocalDate birthDate) {
        int age = Period.between(birthDate, KstTimes.today()).getYears();

        return age >= IDENTITY_MIN_AGE && age < IDENTITY_MAX_AGE;
    }

    private Gender toGender(String gender) {
        if (Gender.MALE.name().equals(gender)) {
            return Gender.MALE;
        }

        if (Gender.FEMALE.name().equals(gender)) {
            return Gender.FEMALE;
        }

        return null;
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
        AnonSessionIdentityVerification identityVerification = requireIdentityVerified(anonSessionId);
        AnonSessionVerificationSession emailSession = requireEmailVerified(anonSessionId);

        AnonSession anonSession = anonSessionRepository.findById(anonSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SIGNUP_SESSION_NOT_FOUND));

        requireProfileCompleted(anonSession);

        String phoneNumber = identityVerification.getPhoneNumber();
        String phoneNumberHash = blindIndexHasher.hash(phoneNumber);
        String email = emailSession.getContact();
        String emailHash = blindIndexHasher.hash(email);
        String ciHash = identityVerification.getCiHash();

        if (userRepository.existsByPhoneNumberHash(phoneNumberHash)) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_REGISTERED);
        }

        if (userRepository.existsByEmailHash(emailHash)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        if (userRepository.existsByCiHash(ciHash)) {
            throw new BusinessException(ErrorCode.IDENTITY_ALREADY_REGISTERED);
        }

        String familyNameHash = blindIndexHasher.hash(anonSession.getFamilyName());
        String givenNameHash = blindIndexHasher.hash(anonSession.getGivenName());
        String organizationHash = blindIndexHasher.hash(anonSession.getOrganization());
        String affiliationHash = blindIndexHasher.hash(anonSession.getAffiliation());
        String affiliationNumberHash = blindIndexHasher.hash(anonSession.getAffiliationNumber());
        String birthDateHash = blindIndexHasher.hash(identityVerification.getBirthDate());

        User user = userRepository.save(
                User.create(
                        anonSession.getNickname(),
                        anonSession.getFamilyName(), familyNameHash,
                        anonSession.getGivenName(), givenNameHash,
                        identityVerification.getGender(),
                        anonSession.getOrganization(), organizationHash,
                        anonSession.getAffiliation(), affiliationHash,
                        anonSession.getAffiliationNumber(), affiliationNumberHash,
                        identityVerification.getBirthDate(), birthDateHash,
                        phoneNumber, phoneNumberHash,
                        email, emailHash,
                        identityVerification.getCi(), ciHash
                )
        );

        anonSessionVerificationSessionRepository.deleteByAnonSessionId(anonSessionId);
        anonSessionIdentityVerificationRepository.deleteByAnonSessionId(anonSessionId);

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

        List<AnonSessionAiChat> anonSessionAiChats = anonSessionAiChatRepository.findAllByAnonSessionId(anonSessionId);

        anonSessionAiChats.forEach(anonSessionAiChat -> aiChatRepository.save(
                AiChat.create(
                        user.getId(),
                        anonSessionAiChat.getSender(),
                        anonSessionAiChat.getMessage(),
                        anonSessionAiChat.getTurnIndex(),
                        anonSessionAiChat.getCreatedAt()
                )
        ));

        anonSessionAiChatRepository.deleteAll(anonSessionAiChats);

        surveyAnswerRepository.deleteByAnonSessionId(anonSessionId);

        anonSessionRepository.delete(anonSession);

        verificationRepository.save(Verification.create(user.getId(), VerificationType.IDENTITY, identityVerification.getVerifiedAt()));
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

    private AnonSessionIdentityVerification requireIdentityVerified(Long anonSessionId) {
        return anonSessionIdentityVerificationRepository.findByAnonSessionId(anonSessionId)
                .filter(AnonSessionIdentityVerification::isVerified)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTITY_VERIFICATION_NOT_COMPLETED));
    }

    private AnonSessionVerificationSession requireEmailVerified(Long anonSessionId) {
        return anonSessionVerificationSessionRepository.findByAnonSessionIdAndType(anonSessionId, VerificationType.EMAIL)
                .filter(session -> session.getVerifiedAt() != null)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_VERIFICATION_NOT_COMPLETED));
    }

    private void requireProfileCompleted(AnonSession anonSession) {
        if (anonSession.getNickname() == null
                || anonSession.getFamilyName() == null
                || anonSession.getGivenName() == null
                || anonSession.getOrganization() == null
                || anonSession.getAffiliation() == null
                || anonSession.getAffiliationNumber() == null) {
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