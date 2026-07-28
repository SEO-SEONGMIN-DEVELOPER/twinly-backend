package com.nidus.twinly.connection.dto.websocket;

import com.nidus.twinly.connection.domain.ConnectionDrainingReason;
import io.swagger.v3.oas.annotations.media.Schema;

public record ConnectionDrainingPayload(
        ConnectionDrainingReason reason,
        @Schema(nullable = true, description = "재연결까지 대기할 시간(ms). 서버가 대기 시간을 지정하지 않으면 null이다.")
        Long retryAfterMs
) {
}
