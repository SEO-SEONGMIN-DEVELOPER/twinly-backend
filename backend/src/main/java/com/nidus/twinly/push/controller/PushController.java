package com.nidus.twinly.push.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.nidus.twinly.push.dto.command.PushTokenRegisterCommand;
import com.nidus.twinly.push.dto.request.PushTokenRegisterRequest;
import com.nidus.twinly.push.service.PushService;
import com.nidus.twinly.user.dto.header.UserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "푸시")
@RestController
@RequiredArgsConstructor
public class PushController {

    private final PushService pushService;

    @Operation(summary = "푸시 토큰 등록")
    @PostMapping("/api/v1/push/tokens")
    public void register(@AuthenticationPrincipal UserInfo userInfo,
                      @Valid @RequestBody PushTokenRegisterRequest request) {
        pushService.register(userInfo.id(), PushTokenRegisterCommand.from(request));
    }

    @Operation(summary = "푸시 토큰 해제")
    @DeleteMapping("/api/v1/push/tokens/{deviceId}")
    public void revoke(@AuthenticationPrincipal UserInfo userInfo,
                       @PathVariable UUID deviceId) {
        pushService.revoke(userInfo.id(), deviceId);
    }
}
