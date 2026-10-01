package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.me.dto.request.MeChangeProfileNicknameRequest;

public record MeChangeProfileNicknameCommand(
        String nickname
) {

    public static MeChangeProfileNicknameCommand from(MeChangeProfileNicknameRequest request) {
        return new MeChangeProfileNicknameCommand(request.nickname());
    }
}
