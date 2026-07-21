package com.nidus.twinly.push.controller;

import jakarta.validation.Valid;
import com.nidus.twinly.push.dto.command.PushTokenRegisterCommand;
import com.nidus.twinly.push.dto.command.PushTokenRevokeCommand;
import com.nidus.twinly.push.dto.request.PushTokenRegisterRequest;
import com.nidus.twinly.push.dto.request.PushTokenRevokeRequest;
import com.nidus.twinly.push.service.PushService;
import com.nidus.twinly.user.annotation.CurrentUser;
import com.nidus.twinly.user.dto.header.UserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PushController {

    private final PushService pushService;

    @PostMapping("/api/v1/push/token")
    public void register(@CurrentUser UserInfo userInfo,
                      @Valid @RequestBody PushTokenRegisterRequest request) {
        pushService.register(userInfo.id(), PushTokenRegisterCommand.from(request));
    }

    @DeleteMapping("/api/v1/push/token")
    public void revoke(@CurrentUser UserInfo userInfo,
                       @Valid @RequestBody PushTokenRevokeRequest request) {
        pushService.revoke(userInfo.id(), PushTokenRevokeCommand.from(request));
    }
}
