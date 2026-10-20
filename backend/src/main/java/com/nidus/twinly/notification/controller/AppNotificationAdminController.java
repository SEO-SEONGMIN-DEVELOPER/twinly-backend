package com.nidus.twinly.notification.controller;

import com.nidus.twinly.common.web.RequestId;
import com.nidus.twinly.notification.dto.command.AppNotificationNoticePublishCommand;
import com.nidus.twinly.notification.dto.request.AppNotificationNoticePublishRequest;
import com.nidus.twinly.notification.dto.response.AppNotificationNoticePublishResponse;
import com.nidus.twinly.notification.dto.response.AppNotificationNoticeRecipientCountResponse;
import com.nidus.twinly.notification.service.AppNotificationNoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "알림 (관리자)")
@RestController
@RequiredArgsConstructor
public class AppNotificationAdminController {

    private final AppNotificationNoticeService appNotificationNoticeService;

    @Operation(summary = "공지 발행",
            description = "활성 가입자(탈퇴·탈퇴 유예·시드 제외)의 알림 피드에 공지를 넣는다. push 가 true 면 이번에 새로 피드를 받은 유저 중 이벤트 알림을 켠 유저에게 notice 타입 푸시도 보낸다. 같은 제목·본문의 공지를 이미 받은 유저는 피드와 푸시 모두 건너뛰므로 다시 호출해도 중복되지 않는다.")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST")
    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
    @ApiResponse(responseCode = "403", description = "FORBIDDEN")
    @PostMapping("/admin/app-notifications/notices")
    public AppNotificationNoticePublishResponse publishNotice(@Valid @RequestBody AppNotificationNoticePublishRequest request) {
        return AppNotificationNoticePublishResponse.from(
                appNotificationNoticeService.publish(AppNotificationNoticePublishCommand.from(request)));
    }

    @Operation(summary = "공지 수신 대상 수", description = "userIds를 비우면 활성 가입자 전체 수, 주면 그중 활성 가입자 수")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST")
    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
    @ApiResponse(responseCode = "403", description = "FORBIDDEN")
    @GetMapping("/admin/app-notifications/notices/recipient-count")
    public AppNotificationNoticeRecipientCountResponse countRecipients(@RequestParam(required = false) List<String> userIds) {
        List<Long> ids = userIds == null ? null : userIds.stream()
                .map(userId -> RequestId.toLong(userId, "userIds"))
                .toList();

        return new AppNotificationNoticeRecipientCountResponse(appNotificationNoticeService.countRecipients(ids));
    }
}
