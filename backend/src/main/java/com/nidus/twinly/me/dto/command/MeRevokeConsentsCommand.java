package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.me.dto.request.MeRevokeConsentsRequest;

import java.util.List;

public record MeRevokeConsentsCommand(
        List<MeRevokeConsentsItemCommand> grants
) {

    public static MeRevokeConsentsCommand from(MeRevokeConsentsRequest request) {
        return new MeRevokeConsentsCommand(
                request.grants().stream().map(MeRevokeConsentsItemCommand::from).toList()
        );
    }
}
