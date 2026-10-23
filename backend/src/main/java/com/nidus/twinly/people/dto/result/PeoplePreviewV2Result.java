package com.nidus.twinly.people.dto.result;

import com.nidus.twinly.common.domain.Gender;

public record PeoplePreviewV2Result(
        String ref,
        Gender gender,
        String organization,
        String birthYear,
        String nickname,
        PeoplePreviewV2PhotoResult profilePhoto,
        String maskedPhotoUrl
) {
}
