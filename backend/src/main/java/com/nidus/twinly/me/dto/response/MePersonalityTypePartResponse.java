package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MePersonalityTypePartResult;

public record MePersonalityTypePartResponse(
        String tagline,
        String description
) {

    public static MePersonalityTypePartResponse from(MePersonalityTypePartResult result) {
        return new MePersonalityTypePartResponse(result.tagline(), result.description());
    }
}
