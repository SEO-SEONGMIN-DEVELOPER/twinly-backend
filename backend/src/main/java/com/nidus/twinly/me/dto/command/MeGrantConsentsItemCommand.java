package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.me.dto.request.MeGrantConsentsItemRequest;

public record MeGrantConsentsItemCommand(
        String policyId,
        String version
) {

    public static MeGrantConsentsItemCommand from(MeGrantConsentsItemRequest request) {
        return new MeGrantConsentsItemCommand(request.policyId(), request.version());
    }
}
