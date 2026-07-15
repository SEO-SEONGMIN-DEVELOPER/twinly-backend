package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.me.dto.request.MeProfileRequest;

public record MeProfileCommand(
        String affiliation
) {

    public static MeProfileCommand from(MeProfileRequest request) {
        return new MeProfileCommand(request.affiliation());
    }
}
