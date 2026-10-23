package com.nidus.twinly.people.dto.response;

import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.people.dto.result.PeoplePreviewV2Result;
import io.swagger.v3.oas.annotations.media.Schema;

public record PeoplePreviewV2Response(
        String ref,
        @Schema(nullable = true)
        Gender gender,
        @Schema(nullable = true)
        String organization,
        @Schema(nullable = true)
        String birthYear,
        @Schema(nullable = true)
        String nickname,
        @Schema(nullable = true)
        PeoplePreviewV2PhotoResponse profilePhoto,
        @Schema(nullable = true)
        String maskedPhotoUrl
) {

    public static PeoplePreviewV2Response from(PeoplePreviewV2Result result) {
        return new PeoplePreviewV2Response(
                result.ref(),
                result.gender(),
                result.organization(),
                result.birthYear(),
                result.nickname(),
                result.profilePhoto() != null ? PeoplePreviewV2PhotoResponse.from(result.profilePhoto()) : null,
                result.maskedPhotoUrl()
        );
    }
}
