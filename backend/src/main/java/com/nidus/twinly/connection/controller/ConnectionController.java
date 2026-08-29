package com.nidus.twinly.connection.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.nidus.twinly.connection.dto.command.ConnectionTokenCommand;
import com.nidus.twinly.connection.dto.request.ConnectionTokenRequest;
import com.nidus.twinly.connection.dto.response.ConnectionTokenResponse;
import com.nidus.twinly.connection.service.ConnectionService;
import com.nidus.twinly.user.dto.header.UserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "커넥션")
@RestController
@RequiredArgsConstructor
public class ConnectionController {

    private final ConnectionService connectionService;

    @Operation(summary = "WebSocket 접속 토큰 발급")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/v1/connection-tokens")
    public ConnectionTokenResponse token(@AuthenticationPrincipal UserInfo userInfo,
                                         @Valid @RequestBody ConnectionTokenRequest request) {
        return ConnectionTokenResponse.from(connectionService.token(userInfo.id(), ConnectionTokenCommand.from(request)));
    }
}
