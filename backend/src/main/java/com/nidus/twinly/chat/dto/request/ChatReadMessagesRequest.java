package com.nidus.twinly.chat.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

public record ChatReadMessagesRequest(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @NotNull Long lastMsgId
) {
}
