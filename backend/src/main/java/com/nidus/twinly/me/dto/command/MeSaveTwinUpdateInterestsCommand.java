package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.me.dto.request.MeSaveTwinUpdateInterestsRequest;

import java.util.List;
import java.util.UUID;

public record MeSaveTwinUpdateInterestsCommand(
        UUID updateId,
        List<String> interests
) {

    public static MeSaveTwinUpdateInterestsCommand from(MeSaveTwinUpdateInterestsRequest request) {
        return new MeSaveTwinUpdateInterestsCommand(request.updateId(), request.interests());
    }
}
