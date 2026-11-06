package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.me.dto.request.MeRegisterInviteCodeRequest;

public record MeRegisterInviteCodeCommand(
        String code
) {

    public static MeRegisterInviteCodeCommand from(MeRegisterInviteCodeRequest request) {
        return new MeRegisterInviteCodeCommand(request.code());
    }
}
