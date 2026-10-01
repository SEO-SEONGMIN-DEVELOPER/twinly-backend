package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MeCheckProfileNicknameResult;

public record MeCheckProfileNicknameResponse(
        Boolean isAvailable
) {

    public static MeCheckProfileNicknameResponse from(MeCheckProfileNicknameResult result) {
        return new MeCheckProfileNicknameResponse(result.isAvailable());
    }
}
