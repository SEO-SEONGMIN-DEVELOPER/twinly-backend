package com.nidus.twinly.auth.controller;

import com.nidus.twinly.anon.dto.snapshot.AnonSessionSnapshot;
import com.nidus.twinly.auth.dto.command.*;
import com.nidus.twinly.auth.dto.request.*;
import com.nidus.twinly.auth.dto.response.*;
import com.nidus.twinly.auth.service.AuthService;
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

    @ApiResponses({
            @ApiResponse(responseCode = "422", description = "EMAIL_DOMAIN_NOT_SUPPORTED"),
            @ApiResponse(responseCode = "502", description = "EMAIL_SEND_FAILED")
    })
    @PostMapping("/api/v1/auth/onboarding/email/send")
    public AuthEmailSendResponse onboardingEmailSend(@AuthenticationPrincipal AnonSessionSnapshot anonSessionSnapshot,
                                                     @Valid @RequestBody AuthEmailSendRequest request) {
        return AuthEmailSendResponse.from(authService.onboardingEmailSend(anonSessionSnapshot, AuthEmailSendCommand.from(request)));
    }

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

    @ApiResponse(responseCode = "502", description = "SMS_SEND_FAILED")
    @PostMapping("/api/v1/auth/onboarding/sms/send")
    public AuthSmsSendResponse onboardingSmsSend(@AuthenticationPrincipal AnonSessionSnapshot anonSessionSnapshot,
                                                 @Valid @RequestBody AuthSmsSendRequest request) {
        return AuthSmsSendResponse.from(authService.onboardingSmsSend(anonSessionSnapshot, AuthSmsSendCommand.from(request)));
    }

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

    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "EMAIL_NOT_REGISTERED"),
            @ApiResponse(responseCode = "502", description = "EMAIL_SEND_FAILED")
    })
    @PostMapping("/api/v1/auth/email/send")
    public AuthEmailSendResponse emailSend(@Valid @RequestBody AuthEmailSendRequest request) {
        return AuthEmailSendResponse.from(authService.emailSend(AuthEmailSendCommand.from(request)));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "VERIFICATION_NOT_FOUND"),
            @ApiResponse(responseCode = "410", description = "VERIFICATION_CODE_EXPIRED"),
            @ApiResponse(responseCode = "422", description = "VERIFICATION_CODE_MISMATCH")
    })
    @PostMapping("/api/v1/auth/email/verify")
    public AuthEmailVerifyResponse emailVerify(@Valid @RequestBody AuthEmailVerifyRequest request) {
        return AuthEmailVerifyResponse.from(authService.emailVerify(AuthEmailVerifyCommand.from(request)));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "PHONE_NOT_REGISTERED"),
            @ApiResponse(responseCode = "502", description = "SMS_SEND_FAILED")
    })
    @PostMapping("/api/v1/auth/sms/send")
    public AuthSmsSendResponse smsSend(@Valid @RequestBody AuthSmsSendRequest request) {
        return AuthSmsSendResponse.from(authService.smsSend(AuthSmsSendCommand.from(request)));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "VERIFICATION_NOT_FOUND"),
            @ApiResponse(responseCode = "410", description = "VERIFICATION_CODE_EXPIRED"),
            @ApiResponse(responseCode = "422", description = "VERIFICATION_CODE_MISMATCH")
    })
    @PostMapping("/api/v1/auth/sms/verify")
    public AuthSmsVerifyResponse smsVerify(@Valid @RequestBody AuthSmsVerifyRequest request) {
        return AuthSmsVerifyResponse.from(authService.smsVerify(AuthSmsVerifyCommand.from(request)));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "SIGNUP_SESSION_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "PHONE_ALREADY_REGISTERED, EMAIL_ALREADY_REGISTERED"),
            @ApiResponse(responseCode = "422", description = "SMS_VERIFICATION_NOT_COMPLETED, EMAIL_VERIFICATION_NOT_COMPLETED, PROFILE_NOT_COMPLETED")
    })
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/v1/auth/signup")
    public AuthSignupResponse signup(@AuthenticationPrincipal AnonSessionSnapshot anonSessionSnapshot) {
        return AuthSignupResponse.from(authService.signup(anonSessionSnapshot));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "VERIFICATION_NOT_FOUND, PHONE_NOT_REGISTERED"),
            @ApiResponse(responseCode = "410", description = "VERIFICATION_EXPIRED")
    })
    @PostMapping("/api/v1/auth/login")
    public AuthLoginResponse login(@Valid @RequestBody AuthLoginRequest request) {
        return AuthLoginResponse.from(authService.login(AuthLoginCommand.from(request)));
    }

    @ApiResponse(responseCode = "401", description = "INVALID_REFRESH_TOKEN, REFRESH_TOKEN_ALREADY_REVOKED")
    @PostMapping("/api/v1/auth/refresh")
    public AuthRefreshResponse refresh(@Valid @RequestBody AuthRefreshRequest request) {
        return AuthRefreshResponse.from(authService.refresh(AuthRefreshCommand.from(request)));
    }

    @PostMapping("/api/v1/auth/logout")
    public void logout(@Valid @RequestBody AuthLogoutRequest request) {
        authService.logout(AuthLogoutCommand.from(request));
    }
}
