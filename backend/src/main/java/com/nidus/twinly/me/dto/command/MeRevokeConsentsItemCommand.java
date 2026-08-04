package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.me.dto.request.MeRevokeConsentsItemRequest;

public record MeRevokeConsentsItemCommand(
        String policyId,
        String version
) {

    public static MeRevokeConsentsItemCommand from(MeRevokeConsentsItemRequest request) {
        return new MeRevokeConsentsItemCommand(request.policyId(), request.version());
    }
}
