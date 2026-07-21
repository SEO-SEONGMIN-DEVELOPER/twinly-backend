package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.me.dto.request.MeRevokeConsentsItemRequest;

public record MeRevokeConsentsItemCommand(
        Long policyId,
        Integer version
) {

    public static MeRevokeConsentsItemCommand from(MeRevokeConsentsItemRequest request) {
        return new MeRevokeConsentsItemCommand(request.policyId(), request.version());
    }
}
