package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MeProfileVisibilityResult;

public record MeProfileVisibilityResponse(
        Boolean affiliationVisible,
        Boolean affiliationNumberVisible
) {

    public static MeProfileVisibilityResponse from(MeProfileVisibilityResult result) {
        return new MeProfileVisibilityResponse(result.affiliationVisible(), result.affiliationNumberVisible());
    }
}
