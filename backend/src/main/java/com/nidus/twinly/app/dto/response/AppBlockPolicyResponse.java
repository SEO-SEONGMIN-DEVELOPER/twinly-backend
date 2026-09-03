package com.nidus.twinly.app.dto.response;

import com.nidus.twinly.app.domain.AppBlockPolicy;
import com.nidus.twinly.app.domain.AppPlatform;
import com.nidus.twinly.app.domain.AppVersionPolicy;
import com.nidus.twinly.app.domain.MaintenanceState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record AppBlockPolicyResponse(
        Maintenance maintenance,
        @Schema(nullable = true) VersionPolicy ios,
        @Schema(nullable = true) VersionPolicy android
) {
    public static AppBlockPolicyResponse from(AppBlockPolicy policy) {
        return new AppBlockPolicyResponse(
                Maintenance.from(policy.maintenance()),
                policy.versionPolicyOf(AppPlatform.IOS).map(VersionPolicy::from).orElse(null),
                policy.versionPolicyOf(AppPlatform.ANDROID).map(VersionPolicy::from).orElse(null));
    }

    public record Maintenance(
            boolean active,
            @Schema(nullable = true) String message,
            @Schema(nullable = true) Instant until
    ) {
        static Maintenance from(MaintenanceState state) {
            return new Maintenance(state.active(), state.message(), state.until());
        }
    }

    public record VersionPolicy(
            String minVersion,
            String storeUrl
    ) {
        static VersionPolicy from(AppVersionPolicy policy) {
            return new VersionPolicy(policy.minVersion().toString(), policy.storeUrl());
        }
    }
}
