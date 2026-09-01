package com.nidus.twinly.auth.controller;

import com.nidus.twinly.anon.dto.snapshot.AnonSessionSnapshot;
import com.nidus.twinly.auth.dto.command.*;
import com.nidus.twinly.auth.dto.request.*;
import com.nidus.twinly.auth.dto.response.*;
import com.nidus.twinly.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증")
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "가입용 이메일 인증번호 발송")
    @ApiResponses({
            @ApiResponse(responseCode = "422", description = "EMAIL_DOMAIN_NOT_SUPPORTED"),
            @ApiResponse(responseCode = "502", description = "EMAIL_SEND_FAILED")
    })
    @PostMapping("/api/v1/auth/onboarding/email/send")
    public AuthEmailSendResponse onboardingEmailSend(@AuthenticationPrincipal AnonSessionSnapshot anonSessionSnapshot,
                                                     @Valid @RequestBody AuthEmailSendRequest request) {
        return AuthEmailSendResponse.from(authService.onboardingEmailSend(anonSessionSnapshot, AuthEmailSendCommand.from(request)));
    }

    @Operation(summary = "가입용 이메일 인증번호 검증")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "VERIFICATION_NOT_FOUND"),
            @ApiResponse(responseCode = "410", description = "VERIFICATION_CODE_EXPIRED"),
            @ApiResponse(responseCode = "422", description = "VERIFICATION_CODE_MISMATCH")
    })
    @PostMapping("/api/v1/auth/onboarding/email/verify")
    public void onboardingEmailVerify(@AuthenticationPrincipal AnonSessionSnapshot anonSessionSnapshot,
                                      @Valid @RequestBody AuthEmailVerifyRequest request) {
        authService.onboardingEmailVerify(anonSessionSnapshot, AuthEmailVerifyCommand.from(request));
    }

    @Operation(summary = "가입용 휴대폰 인증번호 발송")
    @ApiResponse(responseCode = "502", description = "SMS_SEND_FAILED")
    @PostMapping("/api/v1/auth/onboarding/sms/send")
    public AuthSmsSendResponse onboardingSmsSend(@AuthenticationPrincipal AnonSessionSnapshot anonSessionSnapshot,
                                                 @Valid @RequestBody AuthSmsSendRequest request) {
        return AuthSmsSendResponse.from(authService.onboardingSmsSend(anonSessionSnapshot, AuthSmsSendCommand.from(request)));
    }

    @Operation(summary = "가입용 휴대폰 인증번호 검증")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "VERIFICATION_NOT_FOUND"),
            @ApiResponse(responseCode = "410", description = "VERIFICATION_CODE_EXPIRED"),
            @ApiResponse(responseCode = "422", description = "VERIFICATION_CODE_MISMATCH")
    })
    @PostMapping("/api/v1/auth/onboarding/sms/verify")
    public void onboardingSmsVerify(@AuthenticationPrincipal AnonSessionSnapshot anonSessionSnapshot,
                                    @Valid @RequestBody AuthSmsVerifyRequest request) {
        authService.onboardingSmsVerify(anonSessionSnapshot, AuthSmsVerifyCommand.from(request));
    }

    @Operation(summary = "가입용 본인인증 발급")
    @ApiResponses({
            @ApiResponse(responseCode = "409", description = "IDENTITY_ALREADY_VERIFIED"),
            @ApiResponse(responseCode = "429", description = "IDENTITY_RATE_LIMITED")
    })
    @PostMapping("/api/v1/auth/onboarding/identity/prepare")
    public AuthIdentityPrepareResponse onboardingIdentityPrepare(@AuthenticationPrincipal AnonSessionSnapshot anonSessionSnapshot) {
        return AuthIdentityPrepareResponse.from(authService.onboardingIdentityPrepare(anonSessionSnapshot));
    }

    @Operation(summary = "가입용 본인인증 검증")
    @ApiResponses({
            @ApiResponse(responseCode = "409", description = "IDENTITY_ALREADY_REGISTERED"),
            @ApiResponse(responseCode = "422", description = "IDENTITY_NOT_VERIFIED, IDENTITY_AGE_NOT_ALLOWED"),
            @ApiResponse(responseCode = "502", description = "IDENTITY_VERIFICATION_FAILED")
    })
    @PostMapping("/api/v1/auth/onboarding/identity/verify")
    public void onboardingIdentityVerify(@AuthenticationPrincipal AnonSessionSnapshot anonSessionSnapshot) {
        authService.onboardingIdentityVerify(anonSessionSnapshot);
    }

    @Operation(summary = "이메일 인증번호 발송")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "EMAIL_NOT_REGISTERED"),
            @ApiResponse(responseCode = "502", description = "EMAIL_SEND_FAILED")
    })
    @PostMapping("/api/v1/auth/email/send")
    public AuthEmailSendResponse emailSend(@Valid @RequestBody AuthEmailSendRequest request) {
        return AuthEmailSendResponse.from(authService.emailSend(AuthEmailSendCommand.from(request)));
    }

    @Operation(summary = "이메일 인증번호 검증")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "VERIFICATION_NOT_FOUND"),
            @ApiResponse(responseCode = "410", description = "VERIFICATION_CODE_EXPIRED"),
            @ApiResponse(responseCode = "422", description = "VERIFICATION_CODE_MISMATCH")
    })
    @PostMapping("/api/v1/auth/email/verify")
    public AuthEmailVerifyResponse emailVerify(@Valid @RequestBody AuthEmailVerifyRequest request) {
        return AuthEmailVerifyResponse.from(authService.emailVerify(AuthEmailVerifyCommand.from(request)));
    }

    @Operation(summary = "휴대폰 인증번호 발송")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "PHONE_NOT_REGISTERED"),
            @ApiResponse(responseCode = "502", description = "SMS_SEND_FAILED")
    })
    @PostMapping("/api/v1/auth/sms/send")
    public AuthSmsSendResponse smsSend(@Valid @RequestBody AuthSmsSendRequest request) {
        return AuthSmsSendResponse.from(authService.smsSend(AuthSmsSendCommand.from(request)));
    }

    @Operation(summary = "휴대폰 인증번호 검증")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "VERIFICATION_NOT_FOUND"),
            @ApiResponse(responseCode = "410", description = "VERIFICATION_CODE_EXPIRED"),
            @ApiResponse(responseCode = "422", description = "VERIFICATION_CODE_MISMATCH")
    })
    @PostMapping("/api/v1/auth/sms/verify")
    public AuthSmsVerifyResponse smsVerify(@Valid @RequestBody AuthSmsVerifyRequest request) {
        return AuthSmsVerifyResponse.from(authService.smsVerify(AuthSmsVerifyCommand.from(request)));
    }

    @Operation(summary = "회원가입")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "SIGNUP_SESSION_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "PHONE_ALREADY_REGISTERED, EMAIL_ALREADY_REGISTERED, IDENTITY_ALREADY_REGISTERED"),
            @ApiResponse(responseCode = "422", description = "IDENTITY_VERIFICATION_NOT_COMPLETED, EMAIL_VERIFICATION_NOT_COMPLETED, PROFILE_NOT_COMPLETED, REQUIRED_POLICY_NOT_AGREED")
    })
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/v1/auth/signup")
    public AuthSignupResponse signup(@AuthenticationPrincipal AnonSessionSnapshot anonSessionSnapshot) {
        return AuthSignupResponse.from(authService.signup(anonSessionSnapshot));
    }

    @Operation(summary = "로그인")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "VERIFICATION_NOT_FOUND, PHONE_NOT_REGISTERED"),
            @ApiResponse(responseCode = "410", description = "VERIFICATION_EXPIRED")
    })
    @PostMapping("/api/v1/auth/login")
    public AuthLoginResponse login(@Valid @RequestBody AuthLoginRequest request) {
        return AuthLoginResponse.from(authService.login(AuthLoginCommand.from(request)));
    }

    @Operation(summary = "액세스 토큰 재발급")
    @ApiResponse(responseCode = "401", description = "INVALID_REFRESH_TOKEN, REFRESH_TOKEN_ALREADY_REVOKED")
    @PostMapping("/api/v1/auth/refresh")
    public AuthRefreshResponse refresh(@Valid @RequestBody AuthRefreshRequest request) {
        return AuthRefreshResponse.from(authService.refresh(AuthRefreshCommand.from(request)));
    }

    @Operation(summary = "로그아웃")
    @PostMapping("/api/v1/auth/logout")
    public void logout(@Valid @RequestBody AuthLogoutRequest request) {
        authService.logout(AuthLogoutCommand.from(request));
    }
}
