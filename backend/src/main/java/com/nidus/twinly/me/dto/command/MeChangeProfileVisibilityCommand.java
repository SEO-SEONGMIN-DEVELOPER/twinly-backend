package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.me.dto.request.MeChangeProfileVisibilityRequest;

public record MeChangeProfileVisibilityCommand(
        Boolean isVisible
) {

    public static MeChangeProfileVisibilityCommand from(MeChangeProfileVisibilityRequest request) {
        return new MeChangeProfileVisibilityCommand(request.isVisible());
    }
}
