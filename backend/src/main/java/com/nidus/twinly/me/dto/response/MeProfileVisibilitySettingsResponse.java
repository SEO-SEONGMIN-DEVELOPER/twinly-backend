package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MeProfileVisibilitySettingsResult;

public record MeProfileVisibilitySettingsResponse(
        Boolean affiliationVisible,
        Boolean affiliationNumberVisible
) {

    public static MeProfileVisibilitySettingsResponse from(MeProfileVisibilitySettingsResult result) {
        return new MeProfileVisibilitySettingsResponse(result.affiliationVisible(), result.affiliationNumberVisible());
    }
}
