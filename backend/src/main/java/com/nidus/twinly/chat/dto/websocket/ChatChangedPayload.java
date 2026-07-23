package com.nidus.twinly.chat.dto.websocket;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatChangedPayload(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long roomId
) {
}
