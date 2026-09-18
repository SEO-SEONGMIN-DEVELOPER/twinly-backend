package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.me.dto.request.MeInterestsRequest;

import java.util.List;

public record MeInterestsCommand(
        List<String> interests
) {

    public static MeInterestsCommand from(MeInterestsRequest request) {
        return new MeInterestsCommand(request.interests());
    }
}
