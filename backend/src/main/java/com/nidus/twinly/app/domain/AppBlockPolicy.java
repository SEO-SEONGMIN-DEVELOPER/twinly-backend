package com.nidus.twinly.app.domain;

import java.util.Map;
import java.util.Optional;

public record AppBlockPolicy(
        MaintenanceState maintenance,
        Map<AppPlatform, AppVersionPolicy> versionPolicies
) {
    private static final AppBlockPolicy NONE = new AppBlockPolicy(MaintenanceState.none(), Map.of());

    public static AppBlockPolicy none() {
        return NONE;
    }

    public Optional<AppVersionPolicy> versionPolicyOf(AppPlatform platform) {
        return Optional.ofNullable(versionPolicies.get(platform));
    }
}
