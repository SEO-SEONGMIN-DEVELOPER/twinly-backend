package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.me.dto.request.MeStartTwinUpdateRequest;

public record MeStartTwinUpdateCommand(
        Boolean restart
) {

    public static MeStartTwinUpdateCommand from(MeStartTwinUpdateRequest request) {
        return new MeStartTwinUpdateCommand(request.restart());
    }
}
