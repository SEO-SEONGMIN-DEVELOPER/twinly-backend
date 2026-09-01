package com.nidus.twinly.auth.service;

import com.nidus.twinly.aichat.repository.AiChatRepository;
import com.nidus.twinly.aichat.repository.AnonSessionAiChatRepository;
import com.nidus.twinly.anon.dto.snapshot.AnonSessionSnapshot;
import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.anon.repository.AnonSessionAgreementRepository;
import com.nidus.twinly.anon.repository.AnonSessionPersonaElementRepository;
import com.nidus.twinly.anon.repository.AnonSessionPhotoRepository;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import com.nidus.twinly.auth.client.PortOneChannelType;
import com.nidus.twinly.auth.client.PortOneIdentityClient;
import com.nidus.twinly.auth.client.PortOneIdentityVerificationBody;
import com.nidus.twinly.auth.client.PortOneIdentityVerificationStatus;
import com.nidus.twinly.auth.config.PortOneProperties;
import com.nidus.twinly.auth.dto.command.AuthEmailSendCommand;
import com.nidus.twinly.auth.dto.command.AuthEmailVerifyCommand;
import com.nidus.twinly.auth.dto.command.AuthLoginCommand;
import com.nidus.twinly.auth.dto.command.AuthLogoutCommand;
import com.nidus.twinly.auth.dto.command.AuthRefreshCommand;
import com.nidus.twinly.auth.dto.command.AuthSmsSendCommand;
import com.nidus.twinly.auth.dto.command.AuthSmsVerifyCommand;
import com.nidus.twinly.auth.dto.result.AuthEmailSendResult;
import com.nidus.twinly.auth.dto.result.AuthIdentityPrepareResult;
import com.nidus.twinly.auth.dto.result.AuthEmailVerifyResult;
import com.nidus.twinly.auth.dto.result.AuthSmsSendResult;
import com.nidus.twinly.auth.dto.result.AuthSmsVerifyResult;
import com.nidus.twinly.auth.dto.result.AuthTokenResult;
import com.nidus.twinly.auth.entity.AnonSessionIdentityVerification;
import com.nidus.twinly.auth.entity.AnonSessionVerificationSession;
import com.nidus.twinly.auth.entity.RefreshToken;
import com.nidus.twinly.auth.entity.VerificationSession;
import com.nidus.twinly.auth.repository.AnonSessionIdentityVerificationRepository;
import com.nidus.twinly.auth.repository.AnonSessionVerificationSessionRepository;
import com.nidus.twinly.auth.repository.RefreshTokenRepository;
import com.nidus.twinly.auth.repository.VerificationSessionRepository;
import com.nidus.twinly.common.crypto.BlindIndexHasher;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.domain.VerificationType;
import com.nidus.twinly.common.jwt.JwtService;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.legal.repository.AgreementRepository;
import com.nidus.twinly.organization.entity.Organization;
import com.nidus.twinly.onboarding.repository.SurveyAnswerRepository;
import com.nidus.twinly.organization.service.OrganizationCatalog;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PersonaElementRepository;
import com.nidus.twinly.user.repository.PhotoRepository;
import com.nidus.twinly.user.repository.UserRepository;
import com.nidus.twinly.user.repository.VerificationRepository;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AuthServiceUnitTest {

    private static final Long ANON_SESSION_ID = 7L;
    private static final Long USER_ID = 100L;
    private static final String PHONE = "01012345678";
    private static final String EMAIL = "user@test.com";
    private static final String CODE = "123456";
    private static final String IDENTITY_VERIFICATION_ID = "identity-11111111-2222-3333-4444-555555555555";
    private static final String IDENTITY_NAME = "홍길동";
    private static final String IDENTITY_BIRTH_DATE = "1999-03-14";
    private static final String IDENTITY_PHONE = "01087654321";
    private static final String CI = "ci-value";

    private static final AnonSessionSnapshot SNAPSHOT = new AnonSessionSnapshot(
            ANON_SESSION_ID,
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            Instant.parse("2030-01-01T00:00:00Z"),
            "nick",
            "홍", "길동",
            "트윈리대학교", "20250001",
            PHONE, "phoneHash",
            EMAIL, "emailHash",
            Instant.parse("2026-01-01T00:00:00Z")
    );

    @Mock
    VerificationCodeIssuer verificationCodeIssuer;

    @Mock
    JwtService jwtService;

    @Mock
    VerificationService verificationService;

    @Mock
    VerificationSessionRepository verificationSessionRepository;

    @Mock
    AnonSessionVerificationSessionRepository anonSessionVerificationSessionRepository;

    @Mock
    AnonSessionIdentityVerificationRepository anonSessionIdentityVerificationRepository;

    @Mock
    PortOneIdentityClient portOneIdentityClient;

    @Spy
    PortOneProperties portOneProperties = new PortOneProperties("api-secret", Set.of(PortOneChannelType.LIVE));

    @Mock
    AnonSessionRepository anonSessionRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    AnonSessionPhotoRepository anonSessionPhotoRepository;

    @Mock
    AnonSessionAgreementRepository anonSessionAgreementRepository;

    @Mock
    AnonSessionPersonaElementRepository anonSessionPersonaElementRepository;

    @Mock
    AnonSessionAiChatRepository anonSessionAiChatRepository;

    @Mock
    AiChatRepository aiChatRepository;

    @Mock
    SurveyAnswerRepository surveyAnswerRepository;

    @Mock
    RefreshTokenRepository refreshTokenRepository;

    @Mock
    AgreementRepository agreementRepository;

    @Mock
    PhotoRepository photoRepository;

    @Mock
    PersonaElementRepository personaElementRepository;

    @Mock
    VerificationRepository verificationRepository;

    @Mock
    BlindIndexHasher blindIndexHasher;

    @Mock
    OrganizationCatalog organizationCatalog;

    @InjectMocks
    AuthService authService;

    // ---------- 온보딩 인증번호 발송 ----------

    @Test
    @DisplayName("온보딩 이메일 발송: 기존 인증 세션이 없으면 새 세션을 저장하고 발급된 코드를 이메일로 보낸다")
    void onboardingEmailSend_creates_new_session() {
        // given: 해당 익명 세션의 EMAIL 인증 세션이 아직 없음
        given(anonSessionVerificationSessionRepository.findByAnonSessionIdAndType(ANON_SESSION_ID, VerificationType.EMAIL))
                .willReturn(Optional.empty());
        given(verificationCodeIssuer.issue(EMAIL)).willReturn(CODE);
        given(verificationCodeIssuer.codeExpiresAt()).willReturn(Instant.now().plusSeconds(300));

        // when: 온보딩 이메일 인증번호 발송
        AuthEmailSendResult result = authService.onboardingEmailSend(SNAPSHOT, new AuthEmailSendCommand(EMAIL));

        // then: EMAIL 타입 세션이 저장되고, 저장된 토큰이 결과로 나가며, 발급 코드가 담긴 메일이 발송됨
        ArgumentCaptor<AnonSessionVerificationSession> captor =
                ArgumentCaptor.forClass(AnonSessionVerificationSession.class);
        then(anonSessionVerificationSessionRepository).should().save(captor.capture());

        AnonSessionVerificationSession saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(VerificationType.EMAIL);
        assertThat(saved.getAnonSessionId()).isEqualTo(ANON_SESSION_ID);
        assertThat(saved.getContact()).isEqualTo(EMAIL);
        assertThat(saved.getCode()).isEqualTo(CODE);
        assertThat(result.emailVerificationToken()).isEqualTo(saved.getVerificationToken());
        assertThat(result.expiresAt()).isAfter(Instant.now());

        then(verificationCodeIssuer).should().send(VerificationType.EMAIL, EMAIL, CODE);
    }

    @Test
    @DisplayName("온보딩 이메일 발송: 기존 인증 세션이 있으면 저장하지 않고 코드·토큰만 갱신한다")
    void onboardingEmailSend_refreshes_existing_session() {
        // given: 이전에 다른 이메일로 발급된 인증 세션이 이미 존재
        AnonSessionVerificationSession existing = AnonSessionVerificationSession.create(
                VerificationType.EMAIL, ANON_SESSION_ID, "old@test.com", "111111", Instant.now().plusSeconds(60));
        existing.verify();
        UUID oldToken = existing.getVerificationToken();
        given(anonSessionVerificationSessionRepository.findByAnonSessionIdAndType(ANON_SESSION_ID, VerificationType.EMAIL))
                .willReturn(Optional.of(existing));
        given(verificationCodeIssuer.issue(EMAIL)).willReturn(CODE);
        given(verificationCodeIssuer.codeExpiresAt()).willReturn(Instant.now().plusSeconds(300));

        // when: 새 이메일로 다시 인증번호 발송
        AuthEmailSendResult result = authService.onboardingEmailSend(SNAPSHOT, new AuthEmailSendCommand(EMAIL));

        // then: 새로 저장하지 않고 기존 세션의 연락처·토큰이 갱신되며 인증 완료 표시는 초기화됨
        then(anonSessionVerificationSessionRepository).should(never()).save(any());
        assertThat(existing.getContact()).isEqualTo(EMAIL);
        assertThat(existing.getVerificationToken()).isNotEqualTo(oldToken);
        assertThat(existing.getVerifiedAt()).isNull();
        assertThat(result.emailVerificationToken()).isEqualTo(existing.getVerificationToken());
    }

    @Test
    @DisplayName("온보딩 이메일 발송: 가입 가능한 학교의 도메인이 아니면 인증 세션을 만들지도, 메일을 보내지도 않는다")
    void onboardingEmailSend_with_unsupported_domain_throws() {
        // given: 학교 목록에 없는 도메인
        willThrow(new BusinessException(ErrorCode.EMAIL_DOMAIN_NOT_SUPPORTED)).given(organizationCatalog).requireSupportedDomain(EMAIL);

        // when & then: EMAIL_DOMAIN_NOT_SUPPORTED 예외가 발생
        assertThatThrownBy(() -> authService.onboardingEmailSend(SNAPSHOT, new AuthEmailSendCommand(EMAIL)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_DOMAIN_NOT_SUPPORTED);

        // then: 도메인 검증이 코드 발급보다 앞서므로 저장·발송이 일어나지 않음
        then(anonSessionVerificationSessionRepository).should(never()).save(any());
        then(verificationCodeIssuer).should(never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("온보딩 SMS 발송: 새 세션을 저장하고 발급된 코드를 SMS로 보낸다")
    void onboardingSmsSend_creates_new_session() {
        // given: 해당 익명 세션의 SMS 인증 세션이 아직 없음
        given(anonSessionVerificationSessionRepository.findByAnonSessionIdAndType(ANON_SESSION_ID, VerificationType.SMS))
                .willReturn(Optional.empty());
        given(verificationCodeIssuer.issue(PHONE)).willReturn(CODE);
        given(verificationCodeIssuer.codeExpiresAt()).willReturn(Instant.now().plusSeconds(300));

        // when: 온보딩 SMS 인증번호 발송
        AuthSmsSendResult result = authService.onboardingSmsSend(SNAPSHOT, new AuthSmsSendCommand(PHONE));

        // then: SMS 타입 세션이 저장되고, 저장된 토큰이 결과로 나가며, 발급 코드가 담긴 문자가 발송됨
        ArgumentCaptor<AnonSessionVerificationSession> captor =
                ArgumentCaptor.forClass(AnonSessionVerificationSession.class);
        then(anonSessionVerificationSessionRepository).should().save(captor.capture());

        AnonSessionVerificationSession saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(VerificationType.SMS);
        assertThat(saved.getContact()).isEqualTo(PHONE);
        assertThat(result.smsVerificationToken()).isEqualTo(saved.getVerificationToken());

        then(verificationCodeIssuer).should().send(VerificationType.SMS, PHONE, CODE);
    }

    // ---------- 온보딩 인증 확인 ----------

    @Test
    @DisplayName("온보딩 이메일 인증 확인: 인증 세션이 없으면 VERIFICATION_NOT_FOUND 예외가 발생한다")
    void onboardingEmailVerify_without_session_throws() {
        // given: 해당 익명 세션의 EMAIL 인증 세션이 없음
        given(anonSessionVerificationSessionRepository.findByAnonSessionIdAndType(ANON_SESSION_ID, VerificationType.EMAIL))
                .willReturn(Optional.empty());

        // when & then: VERIFICATION_NOT_FOUND 예외 발생
        assertThatThrownBy(() -> authService.onboardingEmailVerify(
                SNAPSHOT, new AuthEmailVerifyCommand(UUID.randomUUID(), "123456")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VERIFICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("온보딩 이메일 인증 확인: 토큰과 코드가 모두 맞으면 세션에 인증 완료 시각이 기록되고 익명 세션에 학교가 채워진다")
    void onboardingEmailVerify_success_marks_verified() {
        // given: 유효 기간이 남은 EMAIL 인증 세션과, 그 이메일 도메인에 해당하는 학교
        AnonSessionVerificationSession session = AnonSessionVerificationSession.create(
                VerificationType.EMAIL, ANON_SESSION_ID, EMAIL, "123456", Instant.now().plusSeconds(60));
        given(anonSessionVerificationSessionRepository.findByAnonSessionIdAndType(ANON_SESSION_ID, VerificationType.EMAIL))
                .willReturn(Optional.of(session));

        AnonSession anonSession = AnonSession.create(SNAPSHOT.token(), Instant.now().plusSeconds(3600));
        given(anonSessionRepository.findById(ANON_SESSION_ID)).willReturn(Optional.of(anonSession));
        given(organizationCatalog.findByEmail(EMAIL)).willReturn(organization("트윈리대학교"));

        // when: 올바른 토큰·코드로 인증 확인
        authService.onboardingEmailVerify(SNAPSHOT, new AuthEmailVerifyCommand(session.getVerificationToken(), "123456"));

        // then: 세션에 인증 완료 시각이 기록되고, 학교는 사용자 입력이 아니라 인증된 도메인으로 결정됨
        assertThat(session.getVerifiedAt()).isNotNull();
        assertThat(anonSession.getOrganization()).isEqualTo("트윈리대학교");
    }

    @Test
    @DisplayName("온보딩 SMS 인증 확인: 인증 토큰이 세션의 토큰과 다르면 VERIFICATION_NOT_FOUND 예외가 발생한다")
    void onboardingSmsVerify_with_wrong_token_throws() {
        // given: SMS 인증 세션이 존재하지만 요청 토큰은 다른 값
        AnonSessionVerificationSession session = AnonSessionVerificationSession.create(
                VerificationType.SMS, ANON_SESSION_ID, PHONE, "123456", Instant.now().plusSeconds(60));
        given(anonSessionVerificationSessionRepository.findByAnonSessionIdAndType(ANON_SESSION_ID, VerificationType.SMS))
                .willReturn(Optional.of(session));

        // when & then: VERIFICATION_NOT_FOUND 예외 발생 + 인증 완료 기록 없음
        assertThatThrownBy(() -> authService.onboardingSmsVerify(
                SNAPSHOT, new AuthSmsVerifyCommand(UUID.randomUUID(), "123456")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VERIFICATION_NOT_FOUND);
        assertThat(session.getVerifiedAt()).isNull();
    }

    @Test
    @DisplayName("온보딩 SMS 인증 확인: 코드 유효 시간이 지났으면 VERIFICATION_CODE_EXPIRED 예외가 발생한다")
    void onboardingSmsVerify_with_expired_code_throws() {
        // given: 코드 만료 시각이 이미 지난 SMS 인증 세션
        AnonSessionVerificationSession session = AnonSessionVerificationSession.create(
                VerificationType.SMS, ANON_SESSION_ID, PHONE, "123456", Instant.now().minusSeconds(1));
        given(anonSessionVerificationSessionRepository.findByAnonSessionIdAndType(ANON_SESSION_ID, VerificationType.SMS))
                .willReturn(Optional.of(session));

        // when & then: VERIFICATION_CODE_EXPIRED 예외 발생
        assertThatThrownBy(() -> authService.onboardingSmsVerify(
                SNAPSHOT, new AuthSmsVerifyCommand(session.getVerificationToken(), "123456")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VERIFICATION_CODE_EXPIRED);
    }

    @Test
    @DisplayName("온보딩 SMS 인증 확인: 코드가 일치하지 않으면 VERIFICATION_CODE_MISMATCH 예외가 발생한다")
    void onboardingSmsVerify_with_wrong_code_throws() {
        // given: 유효 기간이 남은 SMS 인증 세션
        AnonSessionVerificationSession session = AnonSessionVerificationSession.create(
                VerificationType.SMS, ANON_SESSION_ID, PHONE, "123456", Instant.now().plusSeconds(60));
        given(anonSessionVerificationSessionRepository.findByAnonSessionIdAndType(ANON_SESSION_ID, VerificationType.SMS))
                .willReturn(Optional.of(session));

        // when & then: VERIFICATION_CODE_MISMATCH 예외 발생 + 인증 완료 기록 없음
        assertThatThrownBy(() -> authService.onboardingSmsVerify(
                SNAPSHOT, new AuthSmsVerifyCommand(session.getVerificationToken(), "999999")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VERIFICATION_CODE_MISMATCH);
        assertThat(session.getVerifiedAt()).isNull();
    }

    // ---------- 로그인용 인증번호 발송 ----------

    @Test
    @DisplayName("로그인용 이메일 발송: 가입되지 않은 이메일이면 EMAIL_NOT_REGISTERED 예외가 발생하고 저장·발송하지 않는다")
    void emailSend_with_unregistered_email_throws() {
        // given: 해당 이메일 해시로 가입된 유저가 없음
        given(blindIndexHasher.hash(EMAIL)).willReturn("hash:" + EMAIL);
        given(userRepository.existsByEmailHash("hash:" + EMAIL)).willReturn(false);

        // when & then: EMAIL_NOT_REGISTERED 예외 발생 + 세션 저장·메일 발송 없음
        assertThatThrownBy(() -> authService.emailSend(new AuthEmailSendCommand(EMAIL)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_NOT_REGISTERED);

        then(verificationSessionRepository).should(never()).save(any());
        then(verificationCodeIssuer).should(never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("로그인용 이메일 발송: 가입된 이메일이면 인증 세션을 저장하고 코드를 메일로 보낸다")
    void emailSend_success() {
        // given: 해당 이메일 해시로 가입된 유저가 존재
        given(blindIndexHasher.hash(EMAIL)).willReturn("hash:" + EMAIL);
        given(userRepository.existsByEmailHash("hash:" + EMAIL)).willReturn(true);
        given(verificationCodeIssuer.issue(EMAIL)).willReturn(CODE);
        given(verificationCodeIssuer.codeExpiresAt()).willReturn(Instant.now().plusSeconds(300));

        // when: 로그인용 이메일 인증번호 발송
        AuthEmailSendResult result = authService.emailSend(new AuthEmailSendCommand(EMAIL));

        // then: EMAIL 인증 세션이 저장되고 저장된 토큰이 결과로 나가며 코드가 담긴 메일이 발송됨
        ArgumentCaptor<VerificationSession> captor = ArgumentCaptor.forClass(VerificationSession.class);
        then(verificationSessionRepository).should().save(captor.capture());

        VerificationSession saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(VerificationType.EMAIL);
        assertThat(saved.getContact()).isEqualTo(EMAIL);
        assertThat(result.emailVerificationToken()).isEqualTo(saved.getVerificationToken());

        then(verificationCodeIssuer).should().send(VerificationType.EMAIL, EMAIL, CODE);
    }

    @Test
    @DisplayName("로그인용 SMS 발송: 가입되지 않은 전화번호면 PHONE_NOT_REGISTERED 예외가 발생하고 저장·발송하지 않는다")
    void smsSend_with_unregistered_phone_throws() {
        // given: 해당 전화번호 해시로 가입된 유저가 없음
        given(blindIndexHasher.hash(PHONE)).willReturn("hash:" + PHONE);
        given(userRepository.existsByPhoneNumberHash("hash:" + PHONE)).willReturn(false);

        // when & then: PHONE_NOT_REGISTERED 예외 발생 + 세션 저장·문자 발송 없음
        assertThatThrownBy(() -> authService.smsSend(new AuthSmsSendCommand(PHONE)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PHONE_NOT_REGISTERED);

        then(verificationSessionRepository).should(never()).save(any());
        then(verificationCodeIssuer).should(never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("로그인용 SMS 발송: 가입된 전화번호면 인증 세션을 저장하고 코드를 문자로 보낸다")
    void smsSend_success() {
        // given: 해당 전화번호 해시로 가입된 유저가 존재
        given(blindIndexHasher.hash(PHONE)).willReturn("hash:" + PHONE);
        given(userRepository.existsByPhoneNumberHash("hash:" + PHONE)).willReturn(true);
        given(verificationCodeIssuer.issue(PHONE)).willReturn(CODE);
        given(verificationCodeIssuer.codeExpiresAt()).willReturn(Instant.now().plusSeconds(300));

        // when: 로그인용 SMS 인증번호 발송
        AuthSmsSendResult result = authService.smsSend(new AuthSmsSendCommand(PHONE));

        // then: SMS 인증 세션이 저장되고 저장된 토큰이 결과로 나가며 코드가 담긴 문자가 발송됨
        ArgumentCaptor<VerificationSession> captor = ArgumentCaptor.forClass(VerificationSession.class);
        then(verificationSessionRepository).should().save(captor.capture());

        VerificationSession saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(VerificationType.SMS);
        assertThat(saved.getContact()).isEqualTo(PHONE);
        assertThat(result.smsVerificationToken()).isEqualTo(saved.getVerificationToken());

        then(verificationCodeIssuer).should().send(VerificationType.SMS, PHONE, CODE);
    }

    // ---------- 로그인용 인증 확인 ----------

    @Test
    @DisplayName("로그인용 이메일 인증 확인: VerificationService에 위임하고 발급된 인증 완료 토큰을 결과로 옮긴다")
    void emailVerify_delegates_and_maps_result() {
        // given: VerificationService가 인증 완료 토큰이 발급된 세션을 반환
        VerificationSession session = VerificationSession.create(
                VerificationType.EMAIL, EMAIL, "123456", Instant.now().plusSeconds(60));
        session.verify(Instant.now().plus(30, ChronoUnit.MINUTES));
        AuthEmailVerifyCommand command = new AuthEmailVerifyCommand(session.getVerificationToken(), "123456");
        given(verificationService.verify(command, VerificationType.EMAIL)).willReturn(session);

        // when: 로그인용 이메일 인증 확인
        AuthEmailVerifyResult result = authService.emailVerify(command);

        // then: 세션의 인증 완료 토큰·만료 시각이 그대로 결과로 나감
        assertThat(result.emailVerifiedToken()).isEqualTo(session.getVerifiedToken());
        assertThat(result.expiresAt()).isEqualTo(session.getVerifiedTokenExpiresAt());
    }

    @Test
    @DisplayName("로그인용 SMS 인증 확인: VerificationService에 위임하고 발급된 인증 완료 토큰을 결과로 옮긴다")
    void smsVerify_delegates_and_maps_result() {
        // given: VerificationService가 인증 완료 토큰이 발급된 세션을 반환
        VerificationSession session = VerificationSession.create(
                VerificationType.SMS, PHONE, "654321", Instant.now().plusSeconds(60));
        session.verify(Instant.now().plus(30, ChronoUnit.MINUTES));
        AuthSmsVerifyCommand command = new AuthSmsVerifyCommand(session.getVerificationToken(), "654321");
        given(verificationService.verify(command, VerificationType.SMS)).willReturn(session);

        // when: 로그인용 SMS 인증 확인
        AuthSmsVerifyResult result = authService.smsVerify(command);

        // then: 세션의 인증 완료 토큰·만료 시각이 그대로 결과로 나감
        assertThat(result.smsVerifiedToken()).isEqualTo(session.getVerifiedToken());
        assertThat(result.expiresAt()).isEqualTo(session.getVerifiedTokenExpiresAt());
    }

    // ---------- 온보딩 본인인증 ----------

    @Test
    @DisplayName("본인인증 발급: 발급 이력이 없으면 새 인증 건을 저장하고 발급한 id와 만료 시각을 반환한다")
    void identityPrepare_creates_new_verification() {
        // given: 이 익명 세션의 본인인증 행이 아직 없음
        given(anonSessionIdentityVerificationRepository.findByAnonSessionId(ANON_SESSION_ID))
                .willReturn(Optional.empty());

        // when: 본인인증 발급
        AuthIdentityPrepareResult result = authService.onboardingIdentityPrepare(SNAPSHOT);

        // then: 예측 불가능한 id가 세션에 묶여 저장되고, 발급 횟수는 1로 시작한다
        ArgumentCaptor<AnonSessionIdentityVerification> captor =
                ArgumentCaptor.forClass(AnonSessionIdentityVerification.class);
        then(anonSessionIdentityVerificationRepository).should().save(captor.capture());

        AnonSessionIdentityVerification saved = captor.getValue();
        assertThat(saved.getAnonSessionId()).isEqualTo(ANON_SESSION_ID);
        assertThat(saved.getIdentityVerificationId()).startsWith("identity-").isEqualTo(result.identityVerificationId());
        assertThat(saved.getIssueCount()).isEqualTo(1);
        assertThat(saved.isVerified()).isFalse();

        // then: 만료는 30분 뒤이고 응답의 만료 시각과 같다
        assertThat(result.expiresAt()).isEqualTo(saved.getExpiresAt());
        assertThat(result.expiresAt()).isBetween(Instant.now().plus(29, ChronoUnit.MINUTES), Instant.now().plus(30, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("본인인증 발급: 이미 발급 이력이 있으면 새로 저장하지 않고 id를 교체하며 발급 횟수를 올린다")
    void identityPrepare_replaces_previous_id() {
        // given: 아직 인증되지 않은 발급 이력이 1회 존재
        AnonSessionIdentityVerification existing = issuedIdentity();
        String previousId = existing.getIdentityVerificationId();
        given(anonSessionIdentityVerificationRepository.findByAnonSessionId(ANON_SESSION_ID))
                .willReturn(Optional.of(existing));

        // when: 본인인증 재발급
        AuthIdentityPrepareResult result = authService.onboardingIdentityPrepare(SNAPSHOT);

        // then: 새 행을 만들지 않고 기존 행의 id가 교체되어 이전 id는 무효가 된다
        then(anonSessionIdentityVerificationRepository).should(never()).save(any());
        assertThat(existing.getIdentityVerificationId()).isNotEqualTo(previousId).isEqualTo(result.identityVerificationId());

        // then: 발급 횟수가 누적된다 (재발급으로 초기화되지 않는다)
        assertThat(existing.getIssueCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("본인인증 발급: 이미 인증이 끝난 세션이면 IDENTITY_ALREADY_VERIFIED 예외가 발생한다")
    void identityPrepare_when_already_verified_throws() {
        // given: 이미 본인인증을 마친 세션
        given(anonSessionIdentityVerificationRepository.findByAnonSessionId(ANON_SESSION_ID))
                .willReturn(Optional.of(verifiedIdentity()));

        // when & then: 중복 인증(과금) 방지를 위해 409로 끊는다
        assertThatThrownBy(() -> authService.onboardingIdentityPrepare(SNAPSHOT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDENTITY_ALREADY_VERIFIED);
    }

    @Test
    @DisplayName("본인인증 발급: 같은 시간 창에서 5회를 채웠으면 IDENTITY_RATE_LIMITED 예외가 발생하고 id를 바꾸지 않는다")
    void identityPrepare_when_rate_limited_throws() {
        // given: 1시간 창 안에서 이미 5회 발급한 세션
        AnonSessionIdentityVerification existing = issuedIdentity();
        ReflectionTestUtils.setField(existing, "issueCount", 5);
        String previousId = existing.getIdentityVerificationId();
        given(anonSessionIdentityVerificationRepository.findByAnonSessionId(ANON_SESSION_ID))
                .willReturn(Optional.of(existing));

        // when & then: 429로 막고, 발급 상태는 그대로 유지된다
        assertThatThrownBy(() -> authService.onboardingIdentityPrepare(SNAPSHOT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDENTITY_RATE_LIMITED);

        assertThat(existing.getIdentityVerificationId()).isEqualTo(previousId);
        assertThat(existing.getIssueCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("본인인증 발급: 시간 창이 지났으면 발급 횟수를 1로 초기화하고 다시 발급한다")
    void identityPrepare_resets_expired_issue_window() {
        // given: 5회를 채웠지만 발급 창이 1시간을 넘긴 세션
        AnonSessionIdentityVerification existing = issuedIdentity();
        ReflectionTestUtils.setField(existing, "issueCount", 5);
        ReflectionTestUtils.setField(existing, "issueWindowStartedAt", Instant.now().minus(Duration.ofHours(2)));
        given(anonSessionIdentityVerificationRepository.findByAnonSessionId(ANON_SESSION_ID))
                .willReturn(Optional.of(existing));

        // when: 본인인증 발급
        AuthIdentityPrepareResult result = authService.onboardingIdentityPrepare(SNAPSHOT);

        // then: 새 창이 시작되어 발급이 허용된다
        assertThat(result.identityVerificationId()).isEqualTo(existing.getIdentityVerificationId());
        assertThat(existing.getIssueCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("본인인증 검증: PortOne이 인증 완료로 응답하면 이름·생년월일·성별·연락처·CI를 세션에 기록한다")
    void identityVerify_success_records_customer() {
        // given: 발급된 id가 있고, PortOne이 LIVE 채널의 인증 완료 건을 반환
        AnonSessionIdentityVerification issued = issuedIdentity();
        givenIssuedIdentity(issued);
        givenPortOneReturns(verifiedBody(adultBirthDate()));
        given(blindIndexHasher.hash(CI)).willReturn("hash:" + CI);
        given(userRepository.existsByCiHash("hash:" + CI)).willReturn(false);

        // when: 본인인증 검증
        authService.onboardingIdentityVerify(SNAPSHOT);

        // then: 검증된 개인정보가 세션에 기록되고 id가 소비 처리된다
        assertThat(issued.isVerified()).isTrue();
        assertThat(issued.getName()).isEqualTo(IDENTITY_NAME);
        assertThat(issued.getBirthDate()).isEqualTo(adultBirthDate());
        assertThat(issued.getGender()).isEqualTo(Gender.MALE);
        assertThat(issued.getPhoneNumber()).isEqualTo(IDENTITY_PHONE);
        assertThat(issued.getCi()).isEqualTo(CI);
        assertThat(issued.getCiHash()).isEqualTo("hash:" + CI);
    }

    @Test
    @DisplayName("본인인증 검증: 발급 이력이 없으면 IDENTITY_NOT_VERIFIED 예외가 발생하고 PortOne을 조회하지 않는다")
    void identityVerify_without_issued_id_throws() {
        // given: 이 세션에 발급된 본인인증 건이 없음
        given(anonSessionIdentityVerificationRepository.findByAnonSessionId(ANON_SESSION_ID))
                .willReturn(Optional.empty());

        // when & then: 422로 막고 외부 조회는 하지 않는다
        assertThatThrownBy(() -> authService.onboardingIdentityVerify(SNAPSHOT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDENTITY_NOT_VERIFIED);

        then(portOneIdentityClient).should(never()).identityVerification(anyString());
    }

    @Test
    @DisplayName("본인인증 검증: 이미 인증된 세션의 재호출은 PortOne 조회 없이 성공으로 끝난다 (멱등)")
    void identityVerify_when_already_verified_is_idempotent() {
        // given: 이미 인증이 끝난 세션
        given(anonSessionIdentityVerificationRepository.findByAnonSessionId(ANON_SESSION_ID))
                .willReturn(Optional.of(verifiedIdentity()));

        // when: 본인인증 검증 재호출
        authService.onboardingIdentityVerify(SNAPSHOT);

        // then: 외부 조회(과금 유발 지점)를 반복하지 않는다
        then(portOneIdentityClient).should(never()).identityVerification(anyString());
    }

    @Test
    @DisplayName("본인인증 검증: 발급한 id가 만료되었으면 IDENTITY_NOT_VERIFIED 예외가 발생하고 PortOne을 조회하지 않는다")
    void identityVerify_with_expired_id_throws() {
        // given: 만료 시각이 지난 발급 건
        AnonSessionIdentityVerification expired = issuedIdentity();
        ReflectionTestUtils.setField(expired, "expiresAt", Instant.now().minusSeconds(1));
        given(anonSessionIdentityVerificationRepository.findByAnonSessionId(ANON_SESSION_ID))
                .willReturn(Optional.of(expired));

        // when & then: 422로 막고 외부 조회는 하지 않는다
        assertThatThrownBy(() -> authService.onboardingIdentityVerify(SNAPSHOT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDENTITY_NOT_VERIFIED);

        then(portOneIdentityClient).should(never()).identityVerification(anyString());
    }

    @Test
    @DisplayName("본인인증 검증: PortOne에 인증 건이 없으면 IDENTITY_NOT_VERIFIED 예외가 발생한다")
    void identityVerify_when_portone_not_found_throws() {
        // given: PortOne 조회 결과 없음(404)
        givenIssuedIdentity(issuedIdentity());
        given(portOneIdentityClient.identityVerification(IDENTITY_VERIFICATION_ID)).willReturn(Optional.empty());

        // when & then: 인증되지 않은 것과 같게 취급한다
        assertThatThrownBy(() -> authService.onboardingIdentityVerify(SNAPSHOT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDENTITY_NOT_VERIFIED);
    }

    @Test
    @DisplayName("본인인증 검증: PortOne 상태가 READY면 IDENTITY_NOT_VERIFIED 예외가 발생한다")
    void identityVerify_when_status_is_not_verified_throws() {
        // given: 인증창을 열었지만 아직 끝나지 않은 상태
        givenIssuedIdentity(issuedIdentity());
        givenPortOneReturns(new PortOneIdentityVerificationBody(
                PortOneIdentityVerificationStatus.READY,
                new PortOneIdentityVerificationBody.Channel(PortOneChannelType.LIVE),
                customer(adultBirthDate(), IDENTITY_NAME, "MALE", IDENTITY_PHONE, CI)));

        // when & then: VERIFIED가 아니면 통과시키지 않는다
        assertThatThrownBy(() -> authService.onboardingIdentityVerify(SNAPSHOT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDENTITY_NOT_VERIFIED);
    }

    @Test
    @DisplayName("본인인증 검증: 허용되지 않은 TEST 채널 인증 건이면 IDENTITY_NOT_VERIFIED 예외가 발생한다")
    void identityVerify_with_not_allowed_channel_throws() {
        // given: LIVE만 허용하는 환경에서 TEST 채널로 인증된 건
        givenIssuedIdentity(issuedIdentity());
        givenPortOneReturns(new PortOneIdentityVerificationBody(
                PortOneIdentityVerificationStatus.VERIFIED,
                new PortOneIdentityVerificationBody.Channel(PortOneChannelType.TEST),
                customer(adultBirthDate(), IDENTITY_NAME, "MALE", IDENTITY_PHONE, CI)));

        // when & then: 테스트 채널 키로 운영 가입이 뚫리지 않도록 막는다
        assertThatThrownBy(() -> authService.onboardingIdentityVerify(SNAPSHOT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDENTITY_NOT_VERIFIED);
    }

    @Test
    @DisplayName("본인인증 검증: 만 18세 생일 당일은 통과한다 (하한 경계)")
    void identityVerify_at_min_age_boundary_passes() {
        // given: 오늘이 만 18세 생일인 사람
        AnonSessionIdentityVerification issued = issuedIdentity();
        givenIssuedIdentity(issued);
        givenPortOneReturns(verifiedBody(KstTimes.today().minusYears(18).toString()));
        given(blindIndexHasher.hash(CI)).willReturn("hash:" + CI);
        given(userRepository.existsByCiHash("hash:" + CI)).willReturn(false);

        // when: 본인인증 검증
        authService.onboardingIdentityVerify(SNAPSHOT);

        // then: 경계값은 통과한다
        assertThat(issued.isVerified()).isTrue();
    }

    @Test
    @DisplayName("본인인증 검증: 만 18세 생일 하루 전이면 IDENTITY_AGE_NOT_ALLOWED 예외가 발생한다")
    void identityVerify_below_min_age_throws() {
        // given: 만 18세가 되기 하루 전인 사람
        givenIssuedIdentity(issuedIdentity());
        givenPortOneReturns(verifiedBody(KstTimes.today().minusYears(18).plusDays(1).toString()));

        // when & then: 연령 미달로 막는다
        assertThatThrownBy(() -> authService.onboardingIdentityVerify(SNAPSHOT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDENTITY_AGE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("본인인증 검증: 만 29세 생일 당일이면 IDENTITY_AGE_NOT_ALLOWED 예외가 발생한다 (상한 경계)")
    void identityVerify_at_max_age_boundary_throws() {
        // given: 오늘이 만 29세 생일인 사람
        givenIssuedIdentity(issuedIdentity());
        givenPortOneReturns(verifiedBody(KstTimes.today().minusYears(29).toString()));

        // when & then: 상한 경계는 거절한다
        assertThatThrownBy(() -> authService.onboardingIdentityVerify(SNAPSHOT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDENTITY_AGE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("본인인증 검증: 인증은 끝났는데 CI가 없으면 IDENTITY_VERIFICATION_FAILED 예외가 발생한다")
    void identityVerify_without_ci_throws() {
        // given: 인증 완료 응답이지만 CI가 비어 있음
        givenIssuedIdentity(issuedIdentity());
        givenPortOneReturns(new PortOneIdentityVerificationBody(
                PortOneIdentityVerificationStatus.VERIFIED,
                new PortOneIdentityVerificationBody.Channel(PortOneChannelType.LIVE),
                customer(adultBirthDate(), IDENTITY_NAME, "MALE", IDENTITY_PHONE, null)));

        // when & then: 유저 사유가 아니라 연동 이상이므로 5xx로 보고한다
        assertThatThrownBy(() -> authService.onboardingIdentityVerify(SNAPSHOT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDENTITY_VERIFICATION_FAILED);
    }

    @Test
    @DisplayName("본인인증 검증: 우리 성별 값으로 매핑되지 않는 응답이면 IDENTITY_VERIFICATION_FAILED 예외가 발생한다")
    void identityVerify_with_unmappable_gender_throws() {
        // given: 인증 완료 응답이지만 성별이 OTHER
        givenIssuedIdentity(issuedIdentity());
        givenPortOneReturns(new PortOneIdentityVerificationBody(
                PortOneIdentityVerificationStatus.VERIFIED,
                new PortOneIdentityVerificationBody.Channel(PortOneChannelType.LIVE),
                customer(adultBirthDate(), IDENTITY_NAME, "OTHER", IDENTITY_PHONE, CI)));

        // when & then: 저장할 수 없는 값이므로 통과시키지 않는다
        assertThatThrownBy(() -> authService.onboardingIdentityVerify(SNAPSHOT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDENTITY_VERIFICATION_FAILED);
    }

    @Test
    @DisplayName("본인인증 검증: 생년월일 형식이 깨져 있으면 IDENTITY_VERIFICATION_FAILED 예외가 발생한다")
    void identityVerify_with_broken_birth_date_throws() {
        // given: 인증 완료 응답이지만 생년월일이 yyyy-MM-dd 형식이 아님
        givenIssuedIdentity(issuedIdentity());
        givenPortOneReturns(verifiedBody("19990314"));

        // when & then: 연령을 계산할 수 없으므로 5xx로 보고한다
        assertThatThrownBy(() -> authService.onboardingIdentityVerify(SNAPSHOT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDENTITY_VERIFICATION_FAILED);
    }

    @Test
    @DisplayName("본인인증 검증: 같은 CI로 이미 가입한 계정이 있으면 IDENTITY_ALREADY_REGISTERED 예외가 발생하고 세션에 기록하지 않는다")
    void identityVerify_with_duplicated_ci_throws() {
        // given: 인증은 통과했지만 같은 CI의 활성 계정이 이미 존재
        AnonSessionIdentityVerification issued = issuedIdentity();
        givenIssuedIdentity(issued);
        givenPortOneReturns(verifiedBody(adultBirthDate()));
        given(blindIndexHasher.hash(CI)).willReturn("hash:" + CI);
        given(userRepository.existsByCiHash("hash:" + CI)).willReturn(true);

        // when & then: 409로 막고 인증 완료로 기록하지 않는다
        assertThatThrownBy(() -> authService.onboardingIdentityVerify(SNAPSHOT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDENTITY_ALREADY_REGISTERED);

        assertThat(issued.isVerified()).isFalse();
    }

    // ---------- 회원가입 ----------

    @Test
    @DisplayName("회원가입: 본인인증이 완료되지 않았으면 IDENTITY_VERIFICATION_NOT_COMPLETED 예외가 발생하고 유저를 만들지 않는다")
    void signup_without_verified_identity_throws() {
        // given: 본인인증 행 자체가 없음
        given(anonSessionIdentityVerificationRepository.findByAnonSessionId(ANON_SESSION_ID))
                .willReturn(Optional.empty());

        // when & then: 어느 인증이 남았는지 구분되는 코드로 실패하고 유저 저장은 없음
        assertThatThrownBy(() -> authService.signup(SNAPSHOT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDENTITY_VERIFICATION_NOT_COMPLETED);

        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("회원가입: 본인인증은 됐는데 이메일 인증이 없으면 EMAIL_VERIFICATION_NOT_COMPLETED 예외가 발생한다")
    void signup_without_verified_email_throws() {
        // given: 본인인증은 끝났지만 이메일 인증 세션이 없음
        given(anonSessionIdentityVerificationRepository.findByAnonSessionId(ANON_SESSION_ID))
                .willReturn(Optional.of(verifiedIdentity()));
        given(anonSessionVerificationSessionRepository.findByAnonSessionIdAndType(ANON_SESSION_ID, VerificationType.EMAIL))
                .willReturn(Optional.empty());

        // when & then: 본인인증과 구분되는 코드로 실패한다
        assertThatThrownBy(() -> authService.signup(SNAPSHOT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_NOT_COMPLETED);

        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("회원가입: 온보딩 프로필이 비어 있으면 PROFILE_NOT_COMPLETED 예외가 발생하고 해싱·유저 생성으로 넘어가지 않는다")
    void signup_without_completed_profile_throws() {
        // given: 인증은 끝났지만 이름을 아직 입력하지 않은 익명 세션 (anon_sessions의 프로필 컬럼은 nullable)
        givenVerifiedIdentityAndEmail();
        AnonSession anonSession = onboardedAnonSession();
        ReflectionTestUtils.setField(anonSession, "familyName", null);
        given(anonSessionRepository.findById(ANON_SESSION_ID)).willReturn(Optional.of(anonSession));

        // when & then: NPE·NOT NULL 위반으로 500이 되는 대신 422 도메인 에러로 실패한다
        assertThatThrownBy(() -> authService.signup(SNAPSHOT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROFILE_NOT_COMPLETED);

        then(blindIndexHasher).should(never()).hash(any());
        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("회원가입: 이미 가입된 전화번호면 PHONE_ALREADY_REGISTERED 예외가 발생하고 유저를 만들지 않는다")
    void signup_with_already_registered_phone_throws() {
        // given: 인증이 모두 완료됐지만 본인인증으로 확인된 전화번호가 이미 가입되어 있음
        givenVerifiedIdentityAndEmail();
        given(anonSessionRepository.findById(ANON_SESSION_ID)).willReturn(Optional.of(onboardedAnonSession()));
        given(blindIndexHasher.hash(anyString())).willAnswer(invocation -> "hash:" + invocation.getArgument(0));
        given(userRepository.existsByPhoneNumberHash("hash:" + IDENTITY_PHONE)).willReturn(true);

        // when & then: PHONE_ALREADY_REGISTERED 예외 발생 + 유저 저장 없음
        assertThatThrownBy(() -> authService.signup(SNAPSHOT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PHONE_ALREADY_REGISTERED);

        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("회원가입: 검증 시점 이후에 같은 CI가 가입됐으면 IDENTITY_ALREADY_REGISTERED 예외가 발생하고 유저를 만들지 않는다")
    void signup_with_already_registered_ci_throws() {
        // given: 인증은 모두 끝났지만 그 사이 같은 CI로 다른 계정이 생성됨
        givenVerifiedIdentityAndEmail();
        given(anonSessionRepository.findById(ANON_SESSION_ID)).willReturn(Optional.of(onboardedAnonSession()));
        given(blindIndexHasher.hash(anyString())).willAnswer(invocation -> "hash:" + invocation.getArgument(0));
        given(userRepository.existsByPhoneNumberHash("hash:" + IDENTITY_PHONE)).willReturn(false);
        given(userRepository.existsByEmailHash("hash:" + EMAIL)).willReturn(false);
        given(userRepository.existsByCiHash("hash:" + CI)).willReturn(true);

        // when & then: verify와 signup 사이의 경합을 가입 직전에 다시 막는다
        assertThatThrownBy(() -> authService.signup(SNAPSHOT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDENTITY_ALREADY_REGISTERED);

        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("회원가입: 인증이 모두 끝나면 본인인증 값과 익명 세션 프로필로 유저를 만들고 익명 데이터를 정리한 뒤 토큰을 발급한다")
    void signup_success() {
        // given: 본인인증·이메일 인증이 완료되고 온보딩 정보가 채워진 익명 세션
        givenVerifiedIdentityAndEmail();
        AnonSession anonSession = onboardedAnonSession();
        given(anonSessionRepository.findById(ANON_SESSION_ID)).willReturn(Optional.of(anonSession));
        given(blindIndexHasher.hash(anyString())).willAnswer(invocation -> "hash:" + invocation.getArgument(0));
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", USER_ID);
            return user;
        });
        AuthTokenResult tokens = new AuthTokenResult(
                "access-token", Instant.parse("2030-01-01T00:00:00Z"),
                "refresh-token", Instant.parse("2030-01-15T00:00:00Z"));
        given(jwtService.generateAuthTokenResult(USER_ID)).willReturn(tokens);

        // when: 회원가입
        AuthTokenResult result = authService.signup(SNAPSHOT);

        // then: 전화번호·생년월일·성별·CI는 본인인증 값이 정본으로 쓰인다 (익명 세션 입력값을 덮어씀)
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        then(userRepository).should().save(userCaptor.capture());
        User created = userCaptor.getValue();
        assertThat(created.getPhoneNumber()).isEqualTo(IDENTITY_PHONE);
        assertThat(created.getPhoneNumberHash()).isEqualTo("hash:" + IDENTITY_PHONE);
        assertThat(created.getBirthDate()).isEqualTo(IDENTITY_BIRTH_DATE);
        assertThat(created.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(created.getCi()).isEqualTo(CI);
        assertThat(created.getCiHash()).isEqualTo("hash:" + CI);

        // then: 이름은 본인인증 값이 아니라 온보딩 입력값을 그대로 쓴다
        assertThat(created.getFamilyName()).isEqualTo("홍");
        assertThat(created.getGivenName()).isEqualTo("길동");
        assertThat(created.getNickname()).isEqualTo("nick");
        assertThat(created.getEmail()).isEqualTo(EMAIL);

        // then: 익명 세션과 두 인증 저장소가 모두 정리되고 인증 이력 2건(IDENTITY/EMAIL)이 남음
        then(anonSessionRepository).should().delete(anonSession);
        then(anonSessionVerificationSessionRepository).should().deleteByAnonSessionId(ANON_SESSION_ID);
        then(anonSessionIdentityVerificationRepository).should().deleteByAnonSessionId(ANON_SESSION_ID);
        then(verificationRepository).should(times(2)).save(any());

        // then: 발급된 토큰이 그대로 반환되고 리프레시 토큰 해시가 저장됨
        assertThat(result).isEqualTo(tokens);
        ArgumentCaptor<RefreshToken> refreshCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        then(refreshTokenRepository).should().save(refreshCaptor.capture());
        assertThat(refreshCaptor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(refreshCaptor.getValue().getTokenHash()).isEqualTo("hash:refresh-token");
    }

    // ---------- 로그인 ----------

    @Test
    @DisplayName("로그인: 인증 완료 토큰의 유효 시간이 지났으면 VERIFICATION_EXPIRED 예외가 발생한다")
    void login_with_expired_verified_token_throws() {
        // given: 인증 완료 토큰의 만료 시각이 이미 지난 SMS 세션
        VerificationSession session = VerificationSession.create(
                VerificationType.SMS, PHONE, "123456", Instant.now().minusSeconds(600));
        session.verify(Instant.now().minusSeconds(1));
        given(verificationSessionRepository.findByTypeAndVerifiedToken(VerificationType.SMS, session.getVerifiedToken()))
                .willReturn(Optional.of(session));

        // when & then: VERIFICATION_EXPIRED 예외 발생 + 토큰 발급 없음
        assertThatThrownBy(() -> authService.login(new AuthLoginCommand(session.getVerifiedToken())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VERIFICATION_EXPIRED);

        then(refreshTokenRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("로그인: 인증 완료 토큰의 연락처로 유저를 찾아 토큰을 발급하고 리프레시 토큰 해시를 저장한다")
    void login_success() {
        // given: 유효한 SMS 인증 완료 세션과 그 전화번호로 가입된 유저
        VerificationSession session = VerificationSession.create(
                VerificationType.SMS, PHONE, "123456", Instant.now().minusSeconds(60));
        session.verify(Instant.now().plus(30, ChronoUnit.MINUTES));
        given(verificationSessionRepository.findByTypeAndVerifiedToken(VerificationType.SMS, session.getVerifiedToken()))
                .willReturn(Optional.of(session));
        given(blindIndexHasher.hash(anyString())).willAnswer(invocation -> "hash:" + invocation.getArgument(0));

        User user = registeredUser();
        given(userRepository.findByPhoneNumberHash("hash:" + PHONE)).willReturn(Optional.of(user));

        AuthTokenResult tokens = new AuthTokenResult(
                "access-token", Instant.parse("2030-01-01T00:00:00Z"),
                "refresh-token", Instant.parse("2030-01-15T00:00:00Z"));
        given(jwtService.generateAuthTokenResult(USER_ID)).willReturn(tokens);

        // when: 로그인
        AuthTokenResult result = authService.login(new AuthLoginCommand(session.getVerifiedToken()));

        // then: 발급된 토큰이 그대로 반환되고 해당 유저의 리프레시 토큰 해시가 저장됨
        assertThat(result).isEqualTo(tokens);
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        then(refreshTokenRepository).should().save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getTokenHash()).isEqualTo("hash:refresh-token");
    }

    @Test
    @DisplayName("로그인: 인증된 전화번호로 가입된 유저가 없으면 PHONE_NOT_REGISTERED 예외가 발생한다")
    void login_with_unregistered_phone_throws() {
        // given: 유효한 SMS 인증 완료 세션이지만 해당 전화번호로 가입된 유저가 없음
        VerificationSession session = VerificationSession.create(
                VerificationType.SMS, PHONE, "123456", Instant.now().minusSeconds(60));
        session.verify(Instant.now().plus(30, ChronoUnit.MINUTES));
        given(verificationSessionRepository.findByTypeAndVerifiedToken(VerificationType.SMS, session.getVerifiedToken()))
                .willReturn(Optional.of(session));
        given(blindIndexHasher.hash(PHONE)).willReturn("hash:" + PHONE);
        given(userRepository.findByPhoneNumberHash("hash:" + PHONE)).willReturn(Optional.empty());

        // when & then: PHONE_NOT_REGISTERED 예외 발생 + 토큰 발급 없음
        assertThatThrownBy(() -> authService.login(new AuthLoginCommand(session.getVerifiedToken())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PHONE_NOT_REGISTERED);

        then(refreshTokenRepository).should(never()).save(any());
    }

    // ---------- 토큰 재발급 / 로그아웃 ----------

    @Test
    @DisplayName("토큰 재발급: 리프레시 토큰 파싱에 실패하면 INVALID_REFRESH_TOKEN 예외가 발생한다")
    void refresh_with_invalid_jwt_throws() {
        // given: JWT 파싱 단계에서 실패
        given(jwtService.parseRefreshTokenUserId("broken-token")).willThrow(new JwtException("invalid"));

        // when & then: INVALID_REFRESH_TOKEN 예외 발생 + 기존 토큰 삭제 없음
        assertThatThrownBy(() -> authService.refresh(new AuthRefreshCommand("broken-token")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);

        then(refreshTokenRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("토큰 재발급: 저장된 리프레시 토큰이 없으면 REFRESH_TOKEN_ALREADY_REVOKED 예외가 발생한다")
    void refresh_with_revoked_token_throws() {
        // given: JWT는 유효하지만 저장소에 해당 토큰 해시가 없음
        given(jwtService.parseRefreshTokenUserId("refresh-token")).willReturn(USER_ID);
        given(blindIndexHasher.hash("refresh-token")).willReturn("hash:refresh-token");
        given(refreshTokenRepository.findByTokenHash("hash:refresh-token")).willReturn(Optional.empty());

        // when & then: REFRESH_TOKEN_ALREADY_REVOKED 예외 발생
        assertThatThrownBy(() -> authService.refresh(new AuthRefreshCommand("refresh-token")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_ALREADY_REVOKED);
    }

    @Test
    @DisplayName("토큰 재발급: 저장된 토큰의 소유자와 JWT의 userId가 다르면 INVALID_REFRESH_TOKEN 예외가 발생한다")
    void refresh_with_owner_mismatch_throws() {
        // given: 저장된 리프레시 토큰의 소유자가 JWT의 userId와 다름
        given(jwtService.parseRefreshTokenUserId("refresh-token")).willReturn(USER_ID);
        given(blindIndexHasher.hash("refresh-token")).willReturn("hash:refresh-token");
        given(refreshTokenRepository.findByTokenHash("hash:refresh-token"))
                .willReturn(Optional.of(RefreshToken.create(999L, "hash:refresh-token", Instant.now().plusSeconds(600))));

        // when & then: INVALID_REFRESH_TOKEN 예외 발생 + 기존 토큰 삭제 없음
        assertThatThrownBy(() -> authService.refresh(new AuthRefreshCommand("refresh-token")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);

        then(refreshTokenRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("토큰 재발급: JWT는 유효해도 저장된 만료 시각이 지났으면 INVALID_REFRESH_TOKEN 예외가 발생한다")
    void refresh_with_expired_stored_token_throws() {
        // given: JWT 파싱은 통과하지만 DB에 저장된 만료 시각만 과거인 상태 (두 만료 기준이 어긋난 경우)
        given(jwtService.parseRefreshTokenUserId("refresh-token")).willReturn(USER_ID);
        given(blindIndexHasher.hash("refresh-token")).willReturn("hash:refresh-token");
        given(refreshTokenRepository.findByTokenHash("hash:refresh-token"))
                .willReturn(Optional.of(RefreshToken.create(USER_ID, "hash:refresh-token", Instant.now().minusSeconds(1))));

        // when & then: DB가 방어선으로 동작해 거절하고 새 토큰도 발급하지 않는다
        assertThatThrownBy(() -> authService.refresh(new AuthRefreshCommand("refresh-token")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);

        then(refreshTokenRepository).should(never()).delete(any());
        then(refreshTokenRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("토큰 재발급: 유효한 리프레시 토큰이면 기존 토큰을 삭제하고 새 토큰을 발급·저장한다")
    void refresh_success() {
        // given: JWT가 유효하고 저장소에도 같은 소유자의 토큰이 존재
        given(jwtService.parseRefreshTokenUserId("refresh-token")).willReturn(USER_ID);
        given(blindIndexHasher.hash(anyString())).willAnswer(invocation -> "hash:" + invocation.getArgument(0));
        RefreshToken stored = RefreshToken.create(USER_ID, "hash:refresh-token", Instant.now().plusSeconds(600));
        given(refreshTokenRepository.findByTokenHash("hash:refresh-token")).willReturn(Optional.of(stored));

        AuthTokenResult newTokens = new AuthTokenResult(
                "new-access-token", Instant.parse("2030-01-01T00:00:00Z"),
                "new-refresh-token", Instant.parse("2030-01-15T00:00:00Z"));
        given(jwtService.generateAuthTokenResult(USER_ID)).willReturn(newTokens);

        // when: 토큰 재발급
        AuthTokenResult result = authService.refresh(new AuthRefreshCommand("refresh-token"));

        // then: 기존 토큰은 삭제되고 새 리프레시 토큰 해시가 저장되며 새 토큰이 반환됨
        then(refreshTokenRepository).should().delete(stored);
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        then(refreshTokenRepository).should().save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getTokenHash()).isEqualTo("hash:new-refresh-token");
        assertThat(result).isEqualTo(newTokens);
    }

    @Test
    @DisplayName("로그아웃: 리프레시 토큰 해시로 저장된 토큰 삭제를 위임한다")
    void logout_deletes_by_token_hash() {
        // given: 리프레시 토큰의 해시 값
        given(blindIndexHasher.hash("refresh-token")).willReturn("hash:refresh-token");

        // when: 로그아웃
        authService.logout(new AuthLogoutCommand("refresh-token"));

        // then: 해당 해시로 삭제를 위임
        then(refreshTokenRepository).should().deleteByTokenHash("hash:refresh-token");
    }

    // ---------- 헬퍼 ----------

    private void givenVerifiedIdentityAndEmail() {
        AnonSessionVerificationSession emailSession = AnonSessionVerificationSession.create(
                VerificationType.EMAIL, ANON_SESSION_ID, EMAIL, "654321", Instant.now().plusSeconds(60));
        emailSession.verify();

        given(anonSessionIdentityVerificationRepository.findByAnonSessionId(ANON_SESSION_ID))
                .willReturn(Optional.of(verifiedIdentity()));
        given(anonSessionVerificationSessionRepository.findByAnonSessionIdAndType(ANON_SESSION_ID, VerificationType.EMAIL))
                .willReturn(Optional.of(emailSession));
    }

    private void givenIssuedIdentity(AnonSessionIdentityVerification issued) {
        given(anonSessionIdentityVerificationRepository.findByAnonSessionId(ANON_SESSION_ID))
                .willReturn(Optional.of(issued));
    }

    private void givenPortOneReturns(PortOneIdentityVerificationBody body) {
        given(portOneIdentityClient.identityVerification(IDENTITY_VERIFICATION_ID)).willReturn(Optional.of(body));
    }

    private AnonSessionIdentityVerification issuedIdentity() {
        return AnonSessionIdentityVerification.create(
                ANON_SESSION_ID, IDENTITY_VERIFICATION_ID, Instant.now().plus(30, ChronoUnit.MINUTES));
    }

    private AnonSessionIdentityVerification verifiedIdentity() {
        AnonSessionIdentityVerification verification = issuedIdentity();
        verification.verify(IDENTITY_NAME, IDENTITY_BIRTH_DATE, Gender.FEMALE, IDENTITY_PHONE, CI, "hash:" + CI);
        return verification;
    }

    private PortOneIdentityVerificationBody verifiedBody(String birthDate) {
        return new PortOneIdentityVerificationBody(
                PortOneIdentityVerificationStatus.VERIFIED,
                new PortOneIdentityVerificationBody.Channel(PortOneChannelType.LIVE),
                customer(birthDate, IDENTITY_NAME, "MALE", IDENTITY_PHONE, CI));
    }

    private PortOneIdentityVerificationBody.VerifiedCustomer customer(String birthDate,
                                                                     String name,
                                                                     String gender,
                                                                     String phoneNumber,
                                                                     String ci) {
        return new PortOneIdentityVerificationBody.VerifiedCustomer(name, birthDate, gender, phoneNumber, ci);
    }

    private String adultBirthDate() {
        return KstTimes.today().minusYears(25).toString();
    }

    private AnonSession onboardedAnonSession() {
        AnonSession anonSession = AnonSession.create(SNAPSHOT.token(), Instant.now().plusSeconds(3600));
        ReflectionTestUtils.setField(anonSession, "id", ANON_SESSION_ID);
        anonSession.changeNickname("nick");
        anonSession.changeFamilyName("홍");
        anonSession.changeGivenName("길동");
        anonSession.changeOrganization("트윈리대학교");
        anonSession.changeAffiliation("트윈리대학교");
        anonSession.changeAffiliationNumber("20250001");
        return anonSession;
    }

    private Organization organization(String name) {
        Organization organization = BeanUtils.instantiateClass(Organization.class);
        ReflectionTestUtils.setField(organization, "name", name);
        return organization;
    }

    private User registeredUser() {
        User user = User.create(
                "nick",
                "홍", "hash:홍",
                "길동", "hash:길동",
                Gender.MALE,
                "트윈리대학교", "hash:트윈리대학교",
                "트윈리대학교", "hash:트윈리대학교",
                "20250001", "hash:20250001",
                "2000-01-01", "hash:2000-01-01",
                PHONE, "hash:" + PHONE,
                EMAIL, "hash:" + EMAIL, null, null);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }
}
