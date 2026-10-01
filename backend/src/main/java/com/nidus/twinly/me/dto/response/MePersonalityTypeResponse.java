package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MePersonalityTypeResult;

import java.util.List;

public record MePersonalityTypeResponse(
        String name,
        List<String> keywords,
        MePersonalityTypePartResponse adjective,
        MePersonalityTypePartResponse noun,
        String imageUrl
) {

    public static MePersonalityTypeResponse from(MePersonalityTypeResult result) {
        return new MePersonalityTypeResponse(
                result.name(),
                result.keywords(),
                MePersonalityTypePartResponse.from(result.adjective()),
                MePersonalityTypePartResponse.from(result.noun()),
                result.imageUrl()
        );
    }
}
