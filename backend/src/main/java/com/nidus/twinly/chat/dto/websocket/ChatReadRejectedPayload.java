package com.nidus.twinly.chat.dto.websocket;


import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

public record ChatReadRejectedPayload(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(nullable = true, description = "요청에 담겨 있던 roomId 를 그대로 돌려준다. 요청에서 빠졌거나 형식이 잘못됐으면 null이다.")
        Long roomId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(nullable = true, description = "요청에 담겨 있던 lastMsgId 를 그대로 돌려준다. 요청에서 빠졌거나 형식이 잘못됐으면 null이다.")
        Long lastMsgId,
        CommandError error
) {
}
