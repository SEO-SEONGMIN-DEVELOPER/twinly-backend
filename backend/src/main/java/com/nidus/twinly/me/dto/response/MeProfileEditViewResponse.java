package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MeProfileEditViewResult;

public record MeProfileEditViewResponse(
        Long userId,
        String familyName,
        String givenName,
        String affiliation,
        String affiliationNumber,
        String birthDate,
        String profilePhotoUrl
) {

    public static MeProfileEditViewResponse from(MeProfileEditViewResult result) {
        return new MeProfileEditViewResponse(result.userId(), result.familyName(), result.givenName(), result.affiliation(), result.affiliationNumber(), result.birthDate(), result.profilePhotoUrl());
    }
}
