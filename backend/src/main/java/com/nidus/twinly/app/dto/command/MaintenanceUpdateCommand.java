package com.nidus.twinly.app.dto.command;

import com.nidus.twinly.app.dto.request.MaintenanceUpdateRequest;

import java.time.Instant;

public record MaintenanceUpdateCommand(
        boolean active,
        String message,
        Instant until
) {
    public static MaintenanceUpdateCommand from(MaintenanceUpdateRequest request) {
        return new MaintenanceUpdateCommand(request.active(), request.message(), request.until());
    }
}
