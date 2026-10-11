package com.nidus.twinly.people.dto.response;

import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.people.dto.result.PeoplePreviewResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record PeoplePreviewResponse(
        String ref,
        @Schema(nullable = true)
        Gender gender,
        @Schema(nullable = true)
        String organization,
        @Schema(nullable = true)
        String birthYear,
        @Schema(nullable = true)
        String profilePhoto
) {

    public static PeoplePreviewResponse from(PeoplePreviewResult result) {
        return new PeoplePreviewResponse(
                result.ref(),
                result.gender(),
                result.organization(),
                result.birthYear(),
                result.profilePhoto()
        );
    }
}
