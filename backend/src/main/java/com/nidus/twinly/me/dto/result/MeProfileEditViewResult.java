package com.nidus.twinly.me.dto.result;

import com.nidus.twinly.common.photo.ProfilePhotoInfo;

import java.util.List;

public record MeProfileEditViewResult(
        Long userId,
        String familyName,
        String givenName,
        String affiliation,
        String affiliationNumber,
        String birthDate,
        ProfilePhotoInfo profilePhoto,
        List<String> interests
) {
}
