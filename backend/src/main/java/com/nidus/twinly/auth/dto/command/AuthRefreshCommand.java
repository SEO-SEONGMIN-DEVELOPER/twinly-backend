package com.nidus.twinly.auth.dto.command;

import com.nidus.twinly.auth.dto.request.AuthRefreshRequest;

public record AuthRefreshCommand(
        String refreshToken
) {

    public static AuthRefreshCommand from(AuthRefreshRequest request) {
        return new AuthRefreshCommand(request.refreshToken());
    }
}
