package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.me.dto.request.MeCheckProfileNicknameRequest;

public record MeCheckProfileNicknameCommand(
        String nickname
) {

    public static MeCheckProfileNicknameCommand from(MeCheckProfileNicknameRequest request) {
        return new MeCheckProfileNicknameCommand(request.nickname());
    }
}
