package com.nidus.twinly.auth.service;

import com.nidus.twinly.aichat.repository.AiChatRepository;
import com.nidus.twinly.aichat.repository.AnonSessionAiChatRepository;
import com.nidus.twinly.anon.dto.snapshot.AnonSessionSnapshot;
import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.anon.repository.AnonSessionAgreementRepository;
import com.nidus.twinly.anon.repository.AnonSessionPersonaElementRepository;
import com.nidus.twinly.anon.repository.AnonSessionPhotoRepository;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import com.nidus.twinly.auth.dto.command.AuthEmailSendCommand;
import com.nidus.twinly.auth.dto.command.AuthEmailVerifyCommand;
import com.nidus.twinly.auth.dto.command.AuthLoginCommand;
import com.nidus.twinly.auth.dto.command.AuthLogoutCommand;
import com.nidus.twinly.auth.dto.command.AuthRefreshCommand;
import com.nidus.twinly.auth.dto.command.AuthSmsSendCommand;
import com.nidus.twinly.auth.dto.command.AuthSmsVerifyCommand;
import com.nidus.twinly.auth.dto.result.AuthEmailSendResult;
import com.nidus.twinly.auth.dto.result.AuthEmailVerifyResult;
import com.nidus.twinly.auth.dto.result.AuthSmsSendResult;
import com.nidus.twinly.auth.dto.result.AuthSmsVerifyResult;
import com.nidus.twinly.auth.dto.result.AuthTokenResult;
import com.nidus.twinly.auth.entity.AnonSessionVerificationSession;
import com.nidus.twinly.auth.entity.RefreshToken;
import com.nidus.twinly.auth.entity.VerificationSession;
import com.nidus.twinly.auth.repository.AnonSessionVerificationSessionRepository;
import com.nidus.twinly.auth.repository.RefreshTokenRepository;
import com.nidus.twinly.auth.repository.VerificationSessionRepository;
import com.nidus.twinly.common.crypto.BlindIndexHasher;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.domain.VerificationType;
import com.nidus.twinly.common.jwt.JwtService;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
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

    private static final AnonSessionSnapshot SNAPSHOT = new AnonSessionSnapshot(
            ANON_SESSION_ID,
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            Instant.parse("2030-01-01T00:00:00Z"),
            "nick",
            "홍", "길동",
            Gender.MALE,
            "트윈리대학교", "20250001",
            "2000-01-01",
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

    // ---------- 회원가입 ----------

    @Test
    @DisplayName("회원가입: SMS 인증이 완료되지 않았으면 SMS_VERIFICATION_NOT_COMPLETED 예외가 발생하고 유저를 만들지 않는다")
    void signup_without_verified_sms_throws() {
        // given: SMS 인증 세션 자체가 없음
        given(anonSessionVerificationSessionRepository.findByAnonSessionIdAndType(ANON_SESSION_ID, VerificationType.SMS))
                .willReturn(Optional.empty());

        // when & then: 어느 인증이 남았는지 구분되는 코드로 실패하고 유저 저장은 없음
        assertThatThrownBy(() -> authService.signup(SNAPSHOT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SMS_VERIFICATION_NOT_COMPLETED);

        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("회원가입: SMS는 됐는데 이메일 인증이 없으면 EMAIL_VERIFICATION_NOT_COMPLETED 예외가 발생한다")
    void signup_without_verified_email_throws() {
        // given: SMS 인증은 끝났지만 이메일 인증 세션이 없음
        AnonSessionVerificationSession smsSession = AnonSessionVerificationSession.create(
                VerificationType.SMS, ANON_SESSION_ID, PHONE, "123456", Instant.now().plusSeconds(60));
        smsSession.verify();

        given(anonSessionVerificationSessionRepository.findByAnonSessionIdAndType(ANON_SESSION_ID, VerificationType.SMS))
                .willReturn(Optional.of(smsSession));
        given(anonSessionVerificationSessionRepository.findByAnonSessionIdAndType(ANON_SESSION_ID, VerificationType.EMAIL))
                .willReturn(Optional.empty());

        // when & then: SMS와 구분되는 코드로 실패한다
        assertThatThrownBy(() -> authService.signup(SNAPSHOT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_NOT_COMPLETED);

        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("회원가입: 온보딩 프로필이 비어 있으면 PROFILE_NOT_COMPLETED 예외가 발생하고 해싱·유저 생성으로 넘어가지 않는다")
    void signup_without_completed_profile_throws() {
        // given: SMS/EMAIL 인증은 끝났지만 이름을 아직 입력하지 않은 익명 세션 (anon_sessions의 프로필 컬럼은 nullable)
        givenVerifiedSessions();
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
        // given: SMS/EMAIL 인증이 모두 완료된 익명 세션이지만, 전화번호가 이미 가입되어 있음
        givenVerifiedSessions();
        given(anonSessionRepository.findById(ANON_SESSION_ID)).willReturn(Optional.of(onboardedAnonSession()));
        given(blindIndexHasher.hash(anyString())).willAnswer(invocation -> "hash:" + invocation.getArgument(0));
        given(userRepository.existsByPhoneNumberHash("hash:" + PHONE)).willReturn(true);

        // when & then: PHONE_ALREADY_REGISTERED 예외 발생 + 유저 저장 없음
        assertThatThrownBy(() -> authService.signup(SNAPSHOT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PHONE_ALREADY_REGISTERED);

        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("회원가입: 인증이 모두 끝나면 익명 세션 정보로 유저를 만들고 익명 데이터를 정리한 뒤 토큰을 발급한다")
    void signup_success() {
        // given: SMS/EMAIL 인증이 완료되고 온보딩 정보가 채워진 익명 세션
        givenVerifiedSessions();
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

        // then: 인증 세션의 연락처와 익명 세션의 프로필로 유저가 생성됨
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        then(userRepository).should().save(userCaptor.capture());
        User created = userCaptor.getValue();
        assertThat(created.getNickname()).isEqualTo("nick");
        assertThat(created.getPhoneNumber()).isEqualTo(PHONE);
        assertThat(created.getPhoneNumberHash()).isEqualTo("hash:" + PHONE);
        assertThat(created.getEmail()).isEqualTo(EMAIL);
        assertThat(created.getEmailHash()).isEqualTo("hash:" + EMAIL);
        assertThat(created.getGender()).isEqualTo(Gender.MALE);

        // then: 익명 세션과 인증 세션이 정리되고 인증 이력 2건(SMS/EMAIL)이 남음
        then(anonSessionRepository).should().delete(anonSession);
        then(anonSessionVerificationSessionRepository).should().deleteByAnonSessionId(ANON_SESSION_ID);
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

    private void givenVerifiedSessions() {
        AnonSessionVerificationSession smsSession = AnonSessionVerificationSession.create(
                VerificationType.SMS, ANON_SESSION_ID, PHONE, "123456", Instant.now().plusSeconds(60));
        smsSession.verify();
        AnonSessionVerificationSession emailSession = AnonSessionVerificationSession.create(
                VerificationType.EMAIL, ANON_SESSION_ID, EMAIL, "654321", Instant.now().plusSeconds(60));
        emailSession.verify();

        given(anonSessionVerificationSessionRepository.findByAnonSessionIdAndType(ANON_SESSION_ID, VerificationType.SMS))
                .willReturn(Optional.of(smsSession));
        given(anonSessionVerificationSessionRepository.findByAnonSessionIdAndType(ANON_SESSION_ID, VerificationType.EMAIL))
                .willReturn(Optional.of(emailSession));
    }

    private AnonSession onboardedAnonSession() {
        AnonSession anonSession = AnonSession.create(SNAPSHOT.token(), Instant.now().plusSeconds(3600));
        ReflectionTestUtils.setField(anonSession, "id", ANON_SESSION_ID);
        anonSession.changeNickname("nick");
        anonSession.changeFamilyName("홍");
        anonSession.changeGivenName("길동");
        anonSession.changeGender(Gender.MALE);
        anonSession.changeOrganization("트윈리대학교");
        anonSession.changeAffiliation("트윈리대학교");
        anonSession.changeAffiliationNumber("20250001");
        anonSession.changeBirthDate("2000-01-01");
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
                EMAIL, "hash:" + EMAIL);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }
}
