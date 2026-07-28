package com.nidus.twinly.people.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.common.photo.ProfilePhotoInfo;
import com.nidus.twinly.people.dto.result.PeopleEventProfilePhotoResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record PeopleEventProfilePhotoResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        @Schema(nullable = true)
        ProfilePhotoInfo profilePhoto
) {

    public static PeopleEventProfilePhotoResponse from(PeopleEventProfilePhotoResult result) {
        return new PeopleEventProfilePhotoResponse(result.userId(), result.profilePhoto());
    }
}
