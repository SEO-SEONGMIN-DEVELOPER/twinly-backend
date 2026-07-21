package com.nidus.twinly.connection.controller;

import jakarta.validation.Valid;
import com.nidus.twinly.connection.dto.command.ConnectionTokenCommand;
import com.nidus.twinly.connection.dto.request.ConnectionTokenRequest;
import com.nidus.twinly.connection.dto.response.ConnectionTokenResponse;
import com.nidus.twinly.connection.service.ConnectionService;
import com.nidus.twinly.user.annotation.CurrentUser;
import com.nidus.twinly.user.dto.header.UserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ConnectionController {

    private final ConnectionService connectionService;

    @PostMapping("/api/v1/connection-token")
    public ConnectionTokenResponse token(@CurrentUser UserInfo userInfo,
                                         @Valid @RequestBody ConnectionTokenRequest request) {
        return ConnectionTokenResponse.from(connectionService.token(userInfo.id(), ConnectionTokenCommand.from(request)));
    }
}
