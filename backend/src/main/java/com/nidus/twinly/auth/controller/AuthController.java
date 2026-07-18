package com.nidus.twinly.auth.controller;

import com.nidus.twinly.anon.annotation.CurrentAnonSession;
import com.nidus.twinly.anon.dto.snapshot.AnonSessionSnapshot;
import com.nidus.twinly.auth.dto.command.*;
import com.nidus.twinly.auth.dto.request.*;
import com.nidus.twinly.auth.dto.response.*;
import com.nidus.twinly.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/api/v1/auth/onboarding/email/send")
    public AuthEmailSendResponse onboardingEmailSend(@Valid @RequestBody AuthEmailSendRequest request) {
        return AuthEmailSendResponse.from(authService.onboardingEmailSend(AuthEmailSendCommand.from(request)));
    }

    @PostMapping("/api/v1/auth/onboarding/email/verify")
    public AuthEmailVerifyResponse onboardingEmailVerify(@Valid @RequestBody AuthEmailVerifyRequest request) {
        return AuthEmailVerifyResponse.from(authService.onboardingEmailVerify(AuthEmailVerifyCommand.from(request)));
    }

    @PostMapping("/api/v1/auth/onboarding/sms/send")
    public AuthSmsSendResponse onboardingSmsSend(@Valid @RequestBody AuthSmsSendRequest request) {
        return AuthSmsSendResponse.from(authService.onboardingSmsSend(AuthSmsSendCommand.from(request)));
    }

    @PostMapping("/api/v1/auth/onboarding/sms/verify")
    public AuthSmsVerifyResponse onboardingSmsVerify(@Valid @RequestBody AuthSmsVerifyRequest request) {
        return AuthSmsVerifyResponse.from(authService.onboardingSmsVerify(AuthSmsVerifyCommand.from(request)));
    }

    @PostMapping("/api/v1/auth/email/send")
    public AuthEmailSendResponse emailSend(@Valid @RequestBody AuthEmailSendRequest request) {
        return AuthEmailSendResponse.from(authService.emailSend(AuthEmailSendCommand.from(request)));
    }

    @PostMapping("/api/v1/auth/email/verify")
    public AuthEmailVerifyResponse emailVerify(@Valid @RequestBody AuthEmailVerifyRequest request) {
        return AuthEmailVerifyResponse.from(authService.emailVerify(AuthEmailVerifyCommand.from(request)));
    }

    @PostMapping("/api/v1/auth/sms/send")
    public AuthSmsSendResponse smsSend(@Valid @RequestBody AuthSmsSendRequest request) {
        return AuthSmsSendResponse.from(authService.smsSend(AuthSmsSendCommand.from(request)));
    }

    @PostMapping("/api/v1/auth/sms/verify")
    public AuthSmsVerifyResponse smsVerify(@Valid @RequestBody AuthSmsVerifyRequest request) {
        return AuthSmsVerifyResponse.from(authService.smsVerify(AuthSmsVerifyCommand.from(request)));
    }

    @PostMapping("/api/v1/auth/signup")
    public AuthSignupResponse signup(@CurrentAnonSession AnonSessionSnapshot anonSessionSnapshot,
                                      @Valid @RequestBody AuthSignupRequest request) {
        return AuthSignupResponse.from(authService.signup(anonSessionSnapshot, AuthSignupCommand.from(request)));
    }

    @PostMapping("/api/v1/auth/login")
    public AuthLoginResponse login(@Valid @RequestBody AuthLoginRequest request) {
        return AuthLoginResponse.from(authService.login(AuthLoginCommand.from(request)));
    }

    @PostMapping("/api/v1/auth/refresh")
    public AuthRefreshResponse refresh(@Valid @RequestBody AuthRefreshRequest request) {
        return AuthRefreshResponse.from(authService.refresh(AuthRefreshCommand.from(request)));
    }
}
