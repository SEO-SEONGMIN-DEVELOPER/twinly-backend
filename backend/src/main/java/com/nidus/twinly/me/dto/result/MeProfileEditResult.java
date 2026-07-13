package com.nidus.twinly.me.dto.result;

public record MeProfileEditResult(
        Long userId,
        String familyName,
        String givenName,
        String affiliation,
        String affiliationNumber,
        String birthDate,
        Integer height,
        String profilePhotoUrl
) {
}
