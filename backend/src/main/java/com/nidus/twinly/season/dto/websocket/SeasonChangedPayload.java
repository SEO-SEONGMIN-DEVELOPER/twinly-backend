package com.nidus.twinly.season.dto.websocket;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(type = "object", description = "본문 없음. 이 이벤트는 재조회 신호로만 사용된다.")
public record SeasonChangedPayload() {
}
