package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.me.dto.request.MeCompleteTwinUpdateRequest;

import java.util.UUID;

public record MeCompleteTwinUpdateCommand(
        UUID updateId
) {

    public static MeCompleteTwinUpdateCommand from(MeCompleteTwinUpdateRequest request) {
        return new MeCompleteTwinUpdateCommand(request.updateId());
    }
}
