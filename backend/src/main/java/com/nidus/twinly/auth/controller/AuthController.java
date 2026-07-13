package com.nidus.twinly.auth.controller;

import com.nidus.twinly.anon.annotation.CurrentAnonSession;
import com.nidus.twinly.anon.dto.header.AnonSessionInfo;
import com.nidus.twinly.auth.dto.command.*;
import com.nidus.twinly.auth.dto.request.*;
import com.nidus.twinly.auth.dto.response.*;
import com.nidus.twinly.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/api/v1/auth/email/send")
    public AuthEmailSendResponse emailSend(@RequestBody AuthEmailSendRequest request) {
        return AuthEmailSendResponse.from(authService.emailSend(AuthEmailSendCommand.from(request)));
    }

    @PostMapping("/api/v1/auth/email/verify")
    public AuthEmailVerifyResponse emailVerify(@RequestBody AuthEmailVerifyRequest request) {
        return AuthEmailVerifyResponse.from(authService.emailVerify(AuthEmailVerifyCommand.from(request)));
    }

    @PostMapping("/api/v1/auth/sms/send")
    public AuthSmsSendResponse smsSend(@RequestBody AuthSmsSendRequest request) {
        return AuthSmsSendResponse.from(authService.smsSend(AuthSmsSendCommand.from(request)));
    }

    @PostMapping("/api/v1/auth/sms/verify")
    public AuthSmsVerifyResponse smsVerify(@RequestBody AuthSmsVerifyRequest request) {
        return AuthSmsVerifyResponse.from(authService.smsVerify(AuthSmsVerifyCommand.from(request)));
    }

    @PostMapping("/api/v1/auth/signup")
    public AuthSignupResponse signup(@CurrentAnonSession AnonSessionInfo anonSessionInfo,
                                      @RequestBody AuthSignupRequest request) {
        return AuthSignupResponse.from(authService.signup(anonSessionInfo.id(), AuthSignupCommand.from(request)));
    }

    @PostMapping("/api/v1/auth/login")
    public AuthLoginResponse login(@RequestBody AuthLoginRequest request) {
        return AuthLoginResponse.from(authService.login(AuthLoginCommand.from(request)));
    }

    @PostMapping("/api/v1/auth/refresh")
    public AuthRefreshResponse refresh(@RequestBody AuthRefreshRequest request) {
        return AuthRefreshResponse.from(authService.refresh(AuthRefreshCommand.from(request)));
    }
}