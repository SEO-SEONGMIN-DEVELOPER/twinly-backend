package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.me.dto.request.MeProfileRequest;

import java.util.List;

public record MeProfileCommand(
        String affiliation,
        List<String> interests
) {

    public static MeProfileCommand from(MeProfileRequest request) {
        return new MeProfileCommand(request.affiliation(), request.interests());
    }
}
