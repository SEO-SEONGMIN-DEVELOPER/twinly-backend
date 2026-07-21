package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.me.dto.request.MeGrantConsentsRequest;

import java.util.List;

public record MeGrantConsentsCommand(
        List<MeGrantConsentsItemCommand> grants
) {

    public static MeGrantConsentsCommand from(MeGrantConsentsRequest request) {
        return new MeGrantConsentsCommand(
                request.grants().stream().map(MeGrantConsentsItemCommand::from).toList()
        );
    }
}
