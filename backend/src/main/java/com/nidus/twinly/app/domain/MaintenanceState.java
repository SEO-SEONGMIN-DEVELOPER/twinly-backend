package com.nidus.twinly.app.domain;

import java.time.Instant;

public record MaintenanceState(
        boolean active,
        String message,
        Instant until
) {
    private static final MaintenanceState NONE = new MaintenanceState(false, null, null);

    public static MaintenanceState none() {
        return NONE;
    }
}
