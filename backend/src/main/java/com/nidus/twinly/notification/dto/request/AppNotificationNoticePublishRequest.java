package com.nidus.twinly.notification.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AppNotificationNoticePublishRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 2000) String body,
        @NotNull
        @Schema(description = "true 면 피드와 함께 notice 푸시를 보낸다. false 면 피드에만 넣는다.")
        Boolean push,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(nullable = true, description = "받을 유저 id. 비우면 발행 시점의 활성 가입자 전체에게 발행한다.")
        @Size(min = 1) List<@NotNull Long> userIds
) {
}
