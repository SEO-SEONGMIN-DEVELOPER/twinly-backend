package com.nidus.twinly.season.dto.websocket;

import com.fasterxml.jackson.annotation.JsonFormat;

public record SeasonChangedPayload(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long seasonId
) {
}
