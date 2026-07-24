package com.nidus.twinly.me.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

public record MeAppNotificationsReadAllRequest(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @NotNull Long lastAppNotificationId
) {
}
