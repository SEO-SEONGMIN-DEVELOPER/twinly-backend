package com.nidus.twinly.auth.controller;

import com.nidus.twinly.anon.annotation.CurrentAnonSession;
import com.nidus.twinly.anon.dto.header.AnonSessionInfo;
import com.nidus.twinly.auth.dto.command.AuthEmailSendCommand;
import com.nidus.twinly.auth.dto.request.AuthEmailSendRequest;
import com.nidus.twinly.auth.dto.response.AuthEmailSendResponse;
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
}
