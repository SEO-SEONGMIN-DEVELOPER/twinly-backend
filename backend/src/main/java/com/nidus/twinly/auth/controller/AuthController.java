package com.nidus.twinly.auth.controller;

import com.nidus.twinly.auth.dto.command.AuthEmailSendCommand;
import com.nidus.twinly.auth.dto.command.AuthEmailVerifyCommand;
import com.nidus.twinly.auth.dto.command.AuthSmsSendCommand;
import com.nidus.twinly.auth.dto.request.AuthEmailSendRequest;
import com.nidus.twinly.auth.dto.request.AuthEmailVerifyRequest;
import com.nidus.twinly.auth.dto.request.AuthSmsSendRequest;
import com.nidus.twinly.auth.dto.response.AuthEmailSendResponse;
import com.nidus.twinly.auth.dto.response.AuthEmailVerifyResponse;
import com.nidus.twinly.auth.dto.response.AuthSmsSendResponse;
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
}