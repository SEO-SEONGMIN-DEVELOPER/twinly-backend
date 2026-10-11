package com.nidus.twinly.people.dto.result;

import com.nidus.twinly.common.domain.Gender;

public record PeoplePreviewResult(
        String ref,
        Gender gender,
        String organization,
        String birthYear,
        String profilePhoto
) {
}
