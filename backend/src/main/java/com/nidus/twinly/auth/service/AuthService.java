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
import com.nidus.twinly.auth.event.UserSignedUpEvent;
import com.nidus.twinly.auth.repository.RefreshTokenRepository;
import com.nidus.twinly.common.logging.InfoLog;
import com.nidus.twinly.legal.domain.PolicyKind;
import com.nidus.twinly.legal.entity.Agreement;
import com.nidus.twinly.legal.repository.AgreementRepository;
import com.nidus.twinly.legal.service.PolicyCatalog;
import com.nidus.twinly.auth.domain.IdentityVerificationResult;
import com.nidus.twinly.auth.domain.InvalidIdentityResultException;
import com.nidus.twinly.auth.domain.VerifiedIdentity;
import com.nidus.twinly.auth.dto.command.*;
import com.nidus.twinly.auth.dto.result.*;
import com.nidus.twinly.auth.entity.AnonSessionIdentityVerification;
import com.nidus.twinly.auth.entity.AnonSessionVerificationSession;
import com.nidus.twinly.auth.entity.VerificationSession;
import com.nidus.twinly.auth.repository.AnonSessionIdentityVerificationRepository;
import com.nidus.twinly.auth.repository.AnonSessionVerificationSessionRepository;
import com.nidus.twinly.auth.repository.VerificationSessionRepository;
import com.nidus.twinly.common.crypto.BlindIndexHasher;
import com.nidus.twinly.common.domain.VerificationType;
import com.nidus.twinly.common.jwt.JwtService;
import com.nidus.twinly.common.photo.ProfileThumbnailService;
import com.nidus.twinly.common.presign.PhotoPresignResult;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.onboarding.repository.SurveyAnswerRepository;
import com.nidus.twinly.organization.entity.Organization;
import com.nidus.twinly.organization.service.OrganizationCatalog;
import com.nidus.twinly.purchase.writer.EarlySignupGrantWriter;
import com.nidus.twinly.schoolverification.service.StudentIdSubmissionService;
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
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String IDENTITY_REQUEST_NO_PREFIX = "TWINLY-";
    private static final int IDENTITY_EXPIRES_MINUTES = 10;
    private static final Duration IDENTITY_ISSUE_WINDOW = Duration.ofHours(1);
    private static final int IDENTITY_ISSUE_LIMIT = 5;
    private static final int IDENTITY_MIN_AGE = 19;

    private final VerificationCodeIssuer verificationCodeIssuer;
    private final JwtService jwtService;
    private final VerificationService verificationService;
    private final OrganizationCatalog organizationCatalog;
    private final PolicyCatalog policyCatalog;
    private final IdentityVerificationProvider identityVerificationProvider;
    private final IdentityVerificationLogService identityVerificationLogService;

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

    private final EarlySignupGrantWriter earlySignupGrantWriter;
    private final StudentIdSubmissionService studentIdSubmissionService;

    private final BlindIndexHasher blindIndexHasher;
    private final ProfileThumbnailService profileThumbnailService;
    private final ApplicationEventPublisher eventPublisher;

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
        studentIdSubmissionService.discardAnonSubmission(anonSessionId);
    }

    @Transactional
    public AuthIdentityPrepareResult onboardingIdentityPrepare(AnonSessionSnapshot anonSessionSnapshot) {
        Instant now = Instant.now();

        anonSessionRepository.findWithLockById(anonSessionSnapshot.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ANON_SESSION));

        AnonSessionIdentityVerification verification = anonSessionIdentityVerificationRepository
                .findByAnonSessionId(anonSessionSnapshot.id())
                .orElse(null);

        if (verification != null && verification.isVerified()) {
            throw new BusinessException(ErrorCode.IDENTITY_ALREADY_VERIFIED);
        }

        if (identityVerificationLogService.isRateLimited(anonSessionSnapshot.id(), now, IDENTITY_ISSUE_WINDOW, IDENTITY_ISSUE_LIMIT)) {
            throw new BusinessException(ErrorCode.IDENTITY_RATE_LIMITED);
        }

        String requestNo = IDENTITY_REQUEST_NO_PREFIX + UUID.randomUUID();
        Instant expiresAt = now.plus(IDENTITY_EXPIRES_MINUTES, ChronoUnit.MINUTES);
        IdentityAuthUrlResult authUrl = identityVerificationProvider.requestAuthUrl(requestNo);

        identityVerificationLogService.issued(anonSessionSnapshot.id(), requestNo, authUrl.transactionId());

        if (verification == null) {
            anonSessionIdentityVerificationRepository.save(AnonSessionIdentityVerification.create(
                    anonSessionSnapshot.id(), requestNo, authUrl.transactionId(), expiresAt));
        } else {
            verification.refresh(requestNo, authUrl.transactionId(), expiresAt);
        }

        return new AuthIdentityPrepareResult(authUrl.authUrl(), expiresAt, authUrl.returnUrl(), authUrl.closeUrl());
    }

    @Transactional
    public void onboardingIdentityVerify(AnonSessionSnapshot anonSessionSnapshot, AuthIdentityVerifyCommand command) {
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

        if (verification.getTransactionId() == null) {
            throw new IllegalStateException("만료 전 본인인증 건에 transaction_id 가 없습니다. anonSessionId=" + anonSessionId);
        }

        VerifiedIdentity identity;
        try {
            identity = identityVerificationProvider.fetchResult(
                    verification.getRequestNo(), verification.getTransactionId(), command.webTransactionId());
        } catch (InvalidIdentityResultException e) {
            logIdentityResult(verification, IdentityVerificationResult.INVALID_RESULT,
                    isBlank(e.getDi()) ? null : blindIndexHasher.hash(e.getDi()));
            throw e;
        }

        String diHash = blindIndexHasher.hash(identity.di());

        if (!isAllowedAge(identity.birthDate())) {
            logIdentityResult(verification, IdentityVerificationResult.AGE_NOT_ALLOWED, diHash);
            throw new BusinessException(ErrorCode.IDENTITY_AGE_NOT_ALLOWED);
        }

        if (userRepository.existsByDiHash(diHash)) {
            logIdentityResult(verification, IdentityVerificationResult.ALREADY_REGISTERED, diHash);
            throw new BusinessException(ErrorCode.IDENTITY_ALREADY_REGISTERED);
        }

        verification.verify(
                identity.name(),
                identity.birthDate().toString(),
                identity.gender(),
                identity.phoneNumber(),
                identity.di(),
                diHash,
                identity.nationalInfo(),
                identity.mobileCarrier()
        );

        logIdentityResult(verification, IdentityVerificationResult.VERIFIED, diHash);
    }

    private void logIdentityResult(AnonSessionIdentityVerification verification, IdentityVerificationResult result, String diHash) {
        identityVerificationLogService.completed(
                verification.getAnonSessionId(), verification.getRequestNo(), verification.getTransactionId(), result, diHash);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isAllowedAge(LocalDate birthDate) {
        int age = Period.between(birthDate, KstTimes.today()).getYears();

        return age >= IDENTITY_MIN_AGE;
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
        AnonSessionVerificationSession emailSession = verifiedEmailSession(anonSessionId).orElse(null);
        if (emailSession == null) {
            requireStudentIdSubmitted(anonSessionId);
        }

        AnonSession anonSession = anonSessionRepository.findById(anonSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SIGNUP_SESSION_NOT_FOUND));

        requireProfileCompleted(anonSession);

        List<AnonSessionAgreement> anonSessionAgreements = anonSessionAgreementRepository.findAllByAnonSessionId(anonSessionId);

        requireRequiredPoliciesAgreed(anonSessionAgreements);

        String phoneNumber = identityVerification.getPhoneNumber();
        String phoneNumberHash = blindIndexHasher.hash(phoneNumber);
        String email = emailSession != null ? emailSession.getContact() : null;
        String emailHash = email != null ? blindIndexHasher.hash(email) : null;
        String diHash = identityVerification.getDiHash();

        if (userRepository.existsByDiHash(diHash)) {
            throw new BusinessException(ErrorCode.IDENTITY_ALREADY_REGISTERED);
        }

        if (userRepository.existsByPhoneNumberHash(phoneNumberHash)) {
            userRepository.releasePhoneNumber(phoneNumberHash);
            InfoLog.log(log, "다른 본인이 가입하며 기존 계정의 전화번호를 해제했습니다.", field("anonSessionId", anonSessionId));
        }

        if (emailHash != null && userRepository.existsByEmailHash(emailHash)) {
            userRepository.releaseEmail(emailHash);
            InfoLog.log(log, "다른 본인이 가입하며 기존 계정의 이메일을 해제했습니다.", field("anonSessionId", anonSessionId));
        }

        String familyNameHash = blindIndexHasher.hash(anonSession.getFamilyName());
        String givenNameHash = blindIndexHasher.hash(anonSession.getGivenName());
        String organizationHash = blindIndexHasher.hash(anonSession.getOrganization());
        String affiliationHash = blindIndexHasher.hash(anonSession.getAffiliation());
        String affiliationNumberHash = blindIndexHasher.hash(anonSession.getAffiliationNumber());
        String birthDateHash = blindIndexHasher.hash(identityVerification.getBirthDate());

        User user = saveSignupUser(
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
                        identityVerification.getDi(), diHash,
                        identityVerification.getNationalInfo(), identityVerification.getMobileCarrier()
                )
        );

        if (emailSession == null) {
            user.startStudentIdVerification();
        }

        anonSessionVerificationSessionRepository.deleteByAnonSessionId(anonSessionId);
        anonSessionIdentityVerificationRepository.deleteByAnonSessionId(anonSessionId);
        studentIdSubmissionService.transferToUser(anonSessionId, user.getId());

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
        if (emailSession != null) {
            verificationRepository.save(Verification.create(user.getId(), VerificationType.EMAIL, emailSession.getVerifiedAt()));
            earlySignupGrantWriter.assign(user.getId(), identityVerification.getGender(), diHash, Instant.now());
        }

        eventPublisher.publishEvent(new UserSignedUpEvent(user.getId()));

        return issueAuthToken(user.getId());
    }

    private User saveSignupUser(User user) {
        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            ErrorCode errorCode = resolveSignupConflict(e);
            if (errorCode == null) {
                throw e;
            }
            throw new BusinessException(errorCode, e);
        }
    }

    private ErrorCode resolveSignupConflict(DataIntegrityViolationException e) {
        if (!(e.getCause() instanceof ConstraintViolationException violation) || violation.getConstraintName() == null) {
            return null;
        }

        String constraintName = violation.getConstraintName().toLowerCase();

        if (constraintName.contains("uk_users_nickname")) {
            return ErrorCode.NICKNAME_ALREADY_USED;
        }
        if (constraintName.contains("uk_users_di_hash")
                || constraintName.contains("uk_users_phone_number_hash")
                || constraintName.contains("uk_users_email_hash")) {
            return ErrorCode.IDENTITY_ALREADY_REGISTERED;
        }

        return null;
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

    private Optional<AnonSessionVerificationSession> verifiedEmailSession(Long anonSessionId) {
        return anonSessionVerificationSessionRepository.findByAnonSessionIdAndType(anonSessionId, VerificationType.EMAIL)
                .filter(session -> session.getVerifiedAt() != null);
    }

    private void requireStudentIdSubmitted(Long anonSessionId) {
        if (studentIdSubmissionService.currentAnonSubmission(anonSessionId).isEmpty()) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_NOT_COMPLETED);
        }
    }

    private void requireRequiredPoliciesAgreed(List<AnonSessionAgreement> anonSessionAgreements) {
        Set<Long> agreedPolicyIds = anonSessionAgreements.stream()
                .filter(anonSessionAgreement -> anonSessionAgreement.getRevokedAt() == null)
                .map(AnonSessionAgreement::getPolicyId)
                .collect(Collectors.toSet());

        if (!agreedPolicyIds.containsAll(policyCatalog.loadRequiredPolicyIds(PolicyKind.ONBOARDING))) {
            throw new BusinessException(ErrorCode.REQUIRED_POLICY_NOT_AGREED);
        }
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

    public AuthOnboardingStudentIdPresignResult onboardingStudentIdPresign(AnonSessionSnapshot anonSessionSnapshot, AuthOnboardingStudentIdPresignCommand command) {
        PhotoPresignResult presign = studentIdSubmissionService.presignForAnonSession(anonSessionSnapshot.id(), command.contentType());

        return new AuthOnboardingStudentIdPresignResult(presign.uploadUrl(), presign.key(), presign.method(), presign.requiredHeaders(), presign.maxBytes(), presign.expiresAt());
    }

    @Transactional
    public void onboardingStudentIdSubmit(AnonSessionSnapshot anonSessionSnapshot, AuthOnboardingStudentIdSubmitCommand command) {
        studentIdSubmissionService.submitForAnonSession(anonSessionSnapshot.id(), command.organizationName(), command.key());
    }
}
