package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MeProfileEditResult;

public record MeProfileEditResponse(
        Long userId,
        String familyName,
        String givenName,
        String affiliation,
        String affiliationNumber,
        String birthDate,
        String profilePhotoUrl
) {

    public static MeProfileEditResponse from(MeProfileEditResult result) {
        return new MeProfileEditResponse(result.userId(), result.familyName(), result.givenName(), result.affiliation(), result.affiliationNumber(), result.birthDate(), result.profilePhotoUrl());
    }
}
