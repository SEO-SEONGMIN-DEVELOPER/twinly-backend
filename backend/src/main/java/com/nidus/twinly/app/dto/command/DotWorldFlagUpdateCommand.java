package com.nidus.twinly.app.dto.command;

import com.nidus.twinly.app.dto.request.DotWorldFlagUpdateRequest;

public record DotWorldFlagUpdateCommand(
        boolean enabled
) {
    public static DotWorldFlagUpdateCommand from(DotWorldFlagUpdateRequest request) {
        return new DotWorldFlagUpdateCommand(request.enabled());
    }
}
