package com.nidus.twinly.notification.controller;

import com.nidus.twinly.notification.dto.command.AppNotificationNoticePublishCommand;
import com.nidus.twinly.notification.dto.request.AppNotificationNoticePublishRequest;
import com.nidus.twinly.notification.dto.response.AppNotificationNoticePublishResponse;
import com.nidus.twinly.notification.service.AppNotificationNoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "알림 (관리자)")
@RestController
@RequiredArgsConstructor
public class AppNotificationAdminController {

    private final AppNotificationNoticeService appNotificationNoticeService;

    @Operation(summary = "공지 발행",
            description = "활성 가입자(탈퇴·탈퇴 유예·시드 제외)의 알림 피드에 공지를 넣는다. 푸시는 보내지 않는다. 같은 제목·본문의 공지를 이미 받은 유저는 건너뛰므로 다시 호출해도 중복되지 않는다.")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST")
    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
    @ApiResponse(responseCode = "403", description = "FORBIDDEN")
    @PostMapping("/admin/app-notifications/notices")
    public AppNotificationNoticePublishResponse publishNotice(@Valid @RequestBody AppNotificationNoticePublishRequest request) {
        return AppNotificationNoticePublishResponse.from(
                appNotificationNoticeService.publish(AppNotificationNoticePublishCommand.from(request)));
    }
}
