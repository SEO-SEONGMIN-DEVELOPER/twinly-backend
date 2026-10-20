package com.nidus.twinly.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record AppNotificationNoticeRecipientCountResponse(
        @Schema(description = "발행 시 받을 수 있는 활성 가입자 수. 이미 같은 공지를 받은 유저도 포함한다.")
        Long recipientCount
) {
}
