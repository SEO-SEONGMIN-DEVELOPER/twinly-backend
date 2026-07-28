package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.me.dto.request.MeChangeProfileVisibilitySettingRequest;

public record MeChangeProfileVisibilitySettingCommand(
        Boolean isVisible
) {

    public static MeChangeProfileVisibilitySettingCommand from(MeChangeProfileVisibilitySettingRequest request) {
        return new MeChangeProfileVisibilitySettingCommand(request.isVisible());
    }
}
