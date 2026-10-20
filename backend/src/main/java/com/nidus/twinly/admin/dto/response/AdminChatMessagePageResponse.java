package com.nidus.twinly.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.admin.dto.result.AdminChatMessagePageResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public record AdminChatMessagePageResponse(
        @Schema(description = "최신 메시지가 먼저 온다")
        List<Message> messages,
        @Schema(description = "가장 오래된 메시지 id를 beforeId로 넘기면 이전 메시지를 더 받는다")
        Boolean hasMore
) {

    public static AdminChatMessagePageResponse from(AdminChatMessagePageResult result) {
        return new AdminChatMessagePageResponse(
                result.messages().stream()
                        .map(message -> new Message(message.id(), message.senderUserId(), message.type(), message.text(), message.sentAt()))
                        .toList(),
                result.hasMore()
        );
    }

    public record Message(
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            Long id,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            Long senderUserId,
            @Schema(description = "TEXT, PHOTO, VIDEO")
            String type,
            @Schema(nullable = true, description = "TEXT 메시지일 때만 내용이 있다")
            String text,
            Instant sentAt
    ) {
    }
}
