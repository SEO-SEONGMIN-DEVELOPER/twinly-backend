package com.nidus.twinly.me.dto.result;

import com.nidus.twinly.common.photo.ProfilePhotoInfo;

public record MeProfileEditViewResult(
        Long userId,
        String familyName,
        String givenName,
        String affiliation,
        String affiliationNumber,
        String birthDate,
        ProfilePhotoInfo profilePhoto
) {
}
