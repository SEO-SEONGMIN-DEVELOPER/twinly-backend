package com.nidus.twinly.me.dto.request;

import jakarta.validation.constraints.NotNull;

public record MeChangePushNotificationsRequest(
        @NotNull Boolean isEnabled
) {
}
