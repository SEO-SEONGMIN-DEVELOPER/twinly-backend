package com.nidus.twinly.auth.controller;

import com.nidus.twinly.anon.dto.snapshot.AnonSessionSnapshot;
import com.nidus.twinly.anon.service.AnonService;
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
import com.nidus.twinly.auth.service.AuthService;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.user.dto.header.UserInfo;
import com.nidus.twinly.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerUnitTest {

    private static final UUID ANON_TOKEN = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID VERIFICATION_TOKEN = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID VERIFIED_TOKEN = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant EXPIRES_AT = Instant.parse("2030-01-01T00:00:00Z");

    private static final AnonSessionSnapshot ANON_SESSION = new AnonSessionSnapshot(
            7L,
            ANON_TOKEN,
            EXPIRES_AT,
            "nick",
            "홍", "길동",
            Gender.MALE,
            "트윈리대학교", "20250001",
            "2000-01-01",
            "01012345678", "phoneHash",
            "user@test.com", "emailHash",
            Instant.parse("2026-01-01T00:00:00Z")
    );

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthService authService;

    // AuthController가 직접 쓰진 않지만, WebMvcConfig가 두 resolver를 모두 주입받고
    // 각 resolver가 이 서비스에 의존하므로 슬라이스 기동에 필수.
    @MockitoBean
    UserService userService;

    @MockitoBean
    AnonService anonService;

    @BeforeEach
    void setUp() {
        given(userService.resolveByAccessToken(anyString()))
                .willReturn(new UserInfo(1L));
        given(anonService.resolveByToken(any()))
                .willReturn(ANON_SESSION);
    }

    @Test
    @DisplayName("온보딩 이메일 인증번호 발송 성공 시 200과 발급된 인증 토큰을 반환하고 익명 세션·커맨드로 서비스를 호출한다")
    void onboardingEmailSend_success() throws Exception {
        // given: 서비스가 인증 토큰과 만료 시각을 반환
        given(authService.onboardingEmailSend(any(), any()))
                .willReturn(new AuthEmailSendResult(VERIFICATION_TOKEN, EXPIRES_AT));

        // when: 익명 세션 토큰을 붙여 온보딩 이메일 발송 API 호출
        var result = mockMvc.perform(post("/api/v1/auth/onboarding/email/send")
                .header("Authorization", "Bearer " + ANON_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"user@test.com"}
                        """));

        // then: 200 + 인증 토큰 JSON 반환 + 익명 세션 스냅샷·커맨드로 서비스에 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerificationToken").value(VERIFICATION_TOKEN.toString()))
                .andExpect(jsonPath("$.expiresAt").exists());
        then(authService).should().onboardingEmailSend(ANON_SESSION, new AuthEmailSendCommand("user@test.com"));
    }

    @Test
    @DisplayName("온보딩 이메일 인증번호 발송 시 인증 헤더가 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void onboardingEmailSend_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 온보딩 이메일 발송 API 호출
        var result = mockMvc.perform(post("/api/v1/auth/onboarding/email/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"user@test.com"}
                        """));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized());
        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("온보딩 이메일 인증번호 발송 시 익명 세션 토큰이 UUID 형식이 아니면 401을 반환한다")
    void onboardingEmailSend_with_non_uuid_token_returns_401() throws Exception {
        // when: UUID가 아닌 익명 세션 토큰으로 온보딩 이메일 발송 API 호출
        var result = mockMvc.perform(post("/api/v1/auth/onboarding/email/send")
                .header("Authorization", "Bearer not-a-uuid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"user@test.com"}
                        """));

        // then: 401 반환 + 익명 세션 조회·서비스 호출 모두 일어나지 않음
        result.andExpect(status().isUnauthorized());
        then(anonService).should(never()).resolveByToken(any());
        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("온보딩 이메일 인증번호 발송 시 email이 없으면 400을 반환하고 서비스를 호출하지 않는다")
    void onboardingEmailSend_without_email_returns_400() throws Exception {
        // when: email 필드를 비운 채 온보딩 이메일 발송 API 호출
        var result = mockMvc.perform(post("/api/v1/auth/onboarding/email/send")
                .header("Authorization", "Bearer " + ANON_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("온보딩 이메일 인증 확인 성공 시 200을 반환하고 익명 세션·커맨드로 서비스를 호출한다")
    void onboardingEmailVerify_success() throws Exception {
        // when: 인증 토큰과 코드를 담아 온보딩 이메일 인증 확인 API 호출
        var result = mockMvc.perform(post("/api/v1/auth/onboarding/email/verify")
                .header("Authorization", "Bearer " + ANON_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"emailVerificationToken":"%s","code":"123456"}
                        """.formatted(VERIFICATION_TOKEN)));

        // then: 200 반환 + 익명 세션 스냅샷·커맨드로 서비스에 위임
        result.andExpect(status().isOk());
        then(authService).should().onboardingEmailVerify(
                ANON_SESSION, new AuthEmailVerifyCommand(VERIFICATION_TOKEN, "123456"));
    }

    @Test
    @DisplayName("온보딩 이메일 인증 확인 시 code가 없으면 400을 반환하고 서비스를 호출하지 않는다")
    void onboardingEmailVerify_without_code_returns_400() throws Exception {
        // when: code 필드를 비운 채 온보딩 이메일 인증 확인 API 호출
        var result = mockMvc.perform(post("/api/v1/auth/onboarding/email/verify")
                .header("Authorization", "Bearer " + ANON_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"emailVerificationToken":"%s"}
                        """.formatted(VERIFICATION_TOKEN)));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("온보딩 SMS 인증번호 발송 성공 시 200과 발급된 인증 토큰을 반환하고 익명 세션·커맨드로 서비스를 호출한다")
    void onboardingSmsSend_success() throws Exception {
        // given: 서비스가 인증 토큰과 만료 시각을 반환
        given(authService.onboardingSmsSend(any(), any()))
                .willReturn(new AuthSmsSendResult(VERIFICATION_TOKEN, EXPIRES_AT));

        // when: 익명 세션 토큰을 붙여 온보딩 SMS 발송 API 호출
        var result = mockMvc.perform(post("/api/v1/auth/onboarding/sms/send")
                .header("Authorization", "Bearer " + ANON_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"phone":"01012345678"}
                        """));

        // then: 200 + 인증 토큰 JSON 반환 + 익명 세션 스냅샷·커맨드로 서비스에 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.smsVerificationToken").value(VERIFICATION_TOKEN.toString()))
                .andExpect(jsonPath("$.expiresAt").exists());
        then(authService).should().onboardingSmsSend(ANON_SESSION, new AuthSmsSendCommand("01012345678"));
    }

    @Test
    @DisplayName("온보딩 SMS 인증 확인 성공 시 200을 반환하고 익명 세션·커맨드로 서비스를 호출한다")
    void onboardingSmsVerify_success() throws Exception {
        // when: 인증 토큰과 코드를 담아 온보딩 SMS 인증 확인 API 호출
        var result = mockMvc.perform(post("/api/v1/auth/onboarding/sms/verify")
                .header("Authorization", "Bearer " + ANON_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"smsVerificationToken":"%s","code":"654321"}
                        """.formatted(VERIFICATION_TOKEN)));

        // then: 200 반환 + 익명 세션 스냅샷·커맨드로 서비스에 위임
        result.andExpect(status().isOk());
        then(authService).should().onboardingSmsVerify(
                ANON_SESSION, new AuthSmsVerifyCommand(VERIFICATION_TOKEN, "654321"));
    }

    @Test
    @DisplayName("로그인용 이메일 인증번호 발송은 인증 헤더 없이도 200과 인증 토큰을 반환한다")
    void emailSend_success() throws Exception {
        // given: 서비스가 인증 토큰과 만료 시각을 반환
        given(authService.emailSend(any()))
                .willReturn(new AuthEmailSendResult(VERIFICATION_TOKEN, EXPIRES_AT));

        // when: 인증 헤더 없이 이메일 발송 API 호출
        var result = mockMvc.perform(post("/api/v1/auth/email/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"user@test.com"}
                        """));

        // then: 200 + 인증 토큰 JSON 반환 + 커맨드로 서비스에 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerificationToken").value(VERIFICATION_TOKEN.toString()));
        then(authService).should().emailSend(new AuthEmailSendCommand("user@test.com"));
    }

    @Test
    @DisplayName("로그인용 이메일 인증번호 발송 시 email이 없으면 400을 반환하고 서비스를 호출하지 않는다")
    void emailSend_without_email_returns_400() throws Exception {
        // when: email 필드를 비운 채 이메일 발송 API 호출
        var result = mockMvc.perform(post("/api/v1/auth/email/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("로그인용 이메일 인증 확인 성공 시 200과 인증 완료 토큰을 반환한다")
    void emailVerify_success() throws Exception {
        // given: 서비스가 인증 완료 토큰과 만료 시각을 반환
        given(authService.emailVerify(any()))
                .willReturn(new AuthEmailVerifyResult(VERIFIED_TOKEN, EXPIRES_AT));

        // when: 인증 토큰과 코드를 담아 이메일 인증 확인 API 호출
        var result = mockMvc.perform(post("/api/v1/auth/email/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"emailVerificationToken":"%s","code":"123456"}
                        """.formatted(VERIFICATION_TOKEN)));

        // then: 200 + 인증 완료 토큰 JSON 반환 + 커맨드로 서비스에 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerifiedToken").value(VERIFIED_TOKEN.toString()))
                .andExpect(jsonPath("$.expiresAt").exists());
        then(authService).should().emailVerify(new AuthEmailVerifyCommand(VERIFICATION_TOKEN, "123456"));
    }

    @Test
    @DisplayName("로그인용 SMS 인증번호 발송 성공 시 200과 인증 토큰을 반환한다")
    void smsSend_success() throws Exception {
        // given: 서비스가 인증 토큰과 만료 시각을 반환
        given(authService.smsSend(any()))
                .willReturn(new AuthSmsSendResult(VERIFICATION_TOKEN, EXPIRES_AT));

        // when: 전화번호를 담아 SMS 발송 API 호출
        var result = mockMvc.perform(post("/api/v1/auth/sms/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"phone":"01012345678"}
                        """));

        // then: 200 + 인증 토큰 JSON 반환 + 커맨드로 서비스에 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.smsVerificationToken").value(VERIFICATION_TOKEN.toString()));
        then(authService).should().smsSend(new AuthSmsSendCommand("01012345678"));
    }

    @Test
    @DisplayName("로그인용 SMS 인증 확인 성공 시 200과 인증 완료 토큰을 반환한다")
    void smsVerify_success() throws Exception {
        // given: 서비스가 인증 완료 토큰과 만료 시각을 반환
        given(authService.smsVerify(any()))
                .willReturn(new AuthSmsVerifyResult(VERIFIED_TOKEN, EXPIRES_AT));

        // when: 인증 토큰과 코드를 담아 SMS 인증 확인 API 호출
        var result = mockMvc.perform(post("/api/v1/auth/sms/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"smsVerificationToken":"%s","code":"654321"}
                        """.formatted(VERIFICATION_TOKEN)));

        // then: 200 + 인증 완료 토큰 JSON 반환 + 커맨드로 서비스에 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.smsVerifiedToken").value(VERIFIED_TOKEN.toString()));
        then(authService).should().smsVerify(new AuthSmsVerifyCommand(VERIFICATION_TOKEN, "654321"));
    }

    @Test
    @DisplayName("회원가입 성공 시 201과 액세스·리프레시 토큰을 반환하고 익명 세션 스냅샷으로 서비스를 호출한다")
    void signup_success() throws Exception {
        // given: 서비스가 액세스·리프레시 토큰을 반환
        given(authService.signup(any()))
                .willReturn(new AuthTokenResult("access-token", EXPIRES_AT, "refresh-token", EXPIRES_AT));

        // when: 익명 세션 토큰을 붙여 회원가입 API 호출
        var result = mockMvc.perform(post("/api/v1/auth/signup")
                .header("Authorization", "Bearer " + ANON_TOKEN));

        // then: 유저를 새로 만드는 호출이므로 201 + 토큰 JSON 반환 + 익명 세션 스냅샷으로 서비스에 위임
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.accessExpiresAt").exists())
                .andExpect(jsonPath("$.refreshExpiresAt").exists());
        then(authService).should().signup(ANON_SESSION);
    }

    @Test
    @DisplayName("회원가입 시 인증 헤더가 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void signup_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 회원가입 API 호출
        var result = mockMvc.perform(post("/api/v1/auth/signup"));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized());
        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("로그인 성공 시 200과 액세스·리프레시 토큰을 반환하고 커맨드로 서비스를 호출한다")
    void login_success() throws Exception {
        // given: 서비스가 액세스·리프레시 토큰을 반환
        given(authService.login(any()))
                .willReturn(new AuthTokenResult("access-token", EXPIRES_AT, "refresh-token", EXPIRES_AT));

        // when: SMS 인증 완료 토큰을 담아 로그인 API 호출
        var result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"smsVerifiedToken":"%s"}
                        """.formatted(VERIFIED_TOKEN)));

        // then: 200 + 토큰 JSON 반환 + 커맨드로 서비스에 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
        then(authService).should().login(new AuthLoginCommand(VERIFIED_TOKEN));
    }

    @Test
    @DisplayName("로그인 시 smsVerifiedToken이 UUID 형식이 아니면 400을 반환하고 서비스를 호출하지 않는다")
    void login_with_non_uuid_token_returns_400() throws Exception {
        // when: UUID가 아닌 값을 smsVerifiedToken으로 담아 로그인 API 호출
        var result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"smsVerifiedToken":"not-a-uuid"}
                        """));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("토큰 재발급 성공 시 200과 새 액세스·리프레시 토큰을 반환하고 커맨드로 서비스를 호출한다")
    void refresh_success() throws Exception {
        // given: 서비스가 새 액세스·리프레시 토큰을 반환
        given(authService.refresh(any()))
                .willReturn(new AuthTokenResult("new-access-token", EXPIRES_AT, "new-refresh-token", EXPIRES_AT));

        // when: 리프레시 토큰을 담아 재발급 API 호출
        var result = mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken":"old-refresh-token"}
                        """));

        // then: 200 + 새 토큰 JSON 반환 + 커맨드로 서비스에 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));
        then(authService).should().refresh(new AuthRefreshCommand("old-refresh-token"));
    }

    @Test
    @DisplayName("로그아웃 성공 시 200을 반환하고 커맨드로 서비스를 호출한다")
    void logout_success() throws Exception {
        // when: 리프레시 토큰을 담아 로그아웃 API 호출
        var result = mockMvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken":"refresh-token"}
                        """));

        // then: 200 반환 + 커맨드로 서비스에 위임
        result.andExpect(status().isOk());
        then(authService).should().logout(new AuthLogoutCommand("refresh-token"));
    }

    @Test
    @DisplayName("로그아웃 시 refreshToken이 없으면 400을 반환하고 서비스를 호출하지 않는다")
    void logout_without_refresh_token_returns_400() throws Exception {
        // when: refreshToken 필드를 비운 채 로그아웃 API 호출
        var result = mockMvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        verifyNoInteractions(authService);
    }
}
