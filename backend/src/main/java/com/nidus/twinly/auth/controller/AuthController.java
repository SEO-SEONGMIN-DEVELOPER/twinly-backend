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
    public AuthEmailSendResponse onboardingEmailSend(@CurrentAnonSession AnonSessionSnapshot anonSessionSnapshot,
                                                     @Valid @RequestBody AuthEmailSendRequest request) {
        return AuthEmailSendResponse.from(authService.onboardingEmailSend(anonSessionSnapshot, AuthEmailSendCommand.from(request)));
    }

    @PostMapping("/api/v1/auth/onboarding/email/verify")
    public void onboardingEmailVerify(@CurrentAnonSession AnonSessionSnapshot anonSessionSnapshot,
                                      @Valid @RequestBody AuthEmailVerifyRequest request) {
        authService.onboardingEmailVerify(anonSessionSnapshot, AuthEmailVerifyCommand.from(request));
    }

    @PostMapping("/api/v1/auth/onboarding/sms/send")
    public AuthSmsSendResponse onboardingSmsSend(@CurrentAnonSession AnonSessionSnapshot anonSessionSnapshot,
                                                 @Valid @RequestBody AuthSmsSendRequest request) {
        return AuthSmsSendResponse.from(authService.onboardingSmsSend(anonSessionSnapshot, AuthSmsSendCommand.from(request)));
    }

    @PostMapping("/api/v1/auth/onboarding/sms/verify")
    public void onboardingSmsVerify(@CurrentAnonSession AnonSessionSnapshot anonSessionSnapshot,
                                    @Valid @RequestBody AuthSmsVerifyRequest request) {
        authService.onboardingSmsVerify(anonSessionSnapshot, AuthSmsVerifyCommand.from(request));
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
    public AuthSignupResponse signup(@CurrentAnonSession AnonSessionSnapshot anonSessionSnapshot) {
        return AuthSignupResponse.from(authService.signup(anonSessionSnapshot));
    }

    @PostMapping("/api/v1/auth/login")
    public AuthLoginResponse login(@Valid @RequestBody AuthLoginRequest request) {
        return AuthLoginResponse.from(authService.login(AuthLoginCommand.from(request)));
    }

    @PostMapping("/api/v1/auth/refresh")
    public AuthRefreshResponse refresh(@Valid @RequestBody AuthRefreshRequest request) {
        return AuthRefreshResponse.from(authService.refresh(AuthRefreshCommand.from(request)));
    }

    @PostMapping("/api/v1/auth/logout")
    public void logout(@Valid @RequestBody AuthLogoutRequest request) {
        authService.logout(AuthLogoutCommand.from(request));
    }
}
