package com.nidus.twinly.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.admin.domain.SubscriptionKind;
import com.nidus.twinly.admin.dto.result.AdminSubscriptionResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record AdminSubscriptionResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        SubscriptionKind kind,
        Boolean active,
        @Schema(nullable = true)
        Instant expiresAt,
        @Schema(nullable = true, description = "RevenueCat 스토어 값. app_store, play_store, promotional 등")
        String store,
        Instant syncedAt
) {

    public static AdminSubscriptionResponse from(AdminSubscriptionResult result) {
        if (result == null) {
            return null;
        }

        return new AdminSubscriptionResponse(result.userId(), result.kind(), result.active(), result.expiresAt(), result.store(), result.syncedAt());
    }
}
