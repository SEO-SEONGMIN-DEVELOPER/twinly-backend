package com.nidus.twinly.auth.dto.command;

import com.nidus.twinly.auth.dto.request.AuthLogoutRequest;

public record AuthLogoutCommand(
        String refreshToken
) {

    public static AuthLogoutCommand from(AuthLogoutRequest request) {
        return new AuthLogoutCommand(request.refreshToken());
    }
}
