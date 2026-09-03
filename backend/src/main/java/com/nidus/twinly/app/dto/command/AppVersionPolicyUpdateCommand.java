package com.nidus.twinly.app.dto.command;

import com.nidus.twinly.app.domain.AppPlatform;
import com.nidus.twinly.app.domain.AppVersion;
import com.nidus.twinly.app.dto.request.AppVersionPolicyUpdateRequest;

public record AppVersionPolicyUpdateCommand(
        AppPlatform platform,
        AppVersion minVersion,
        String storeUrl
) {
    public static AppVersionPolicyUpdateCommand from(AppPlatform platform, AppVersionPolicyUpdateRequest request) {
        return new AppVersionPolicyUpdateCommand(platform, request.minVersion(), request.storeUrl());
    }
}
