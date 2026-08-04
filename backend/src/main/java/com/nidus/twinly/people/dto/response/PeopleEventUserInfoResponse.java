package com.nidus.twinly.people.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.common.photo.ProfilePhotoInfo;
import com.nidus.twinly.people.dto.result.PeopleEventUserInfoResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record PeopleEventUserInfoResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        String userName,
        @Schema(nullable = true)
        ProfilePhotoInfo profilePhoto
) {

    public static PeopleEventUserInfoResponse from(PeopleEventUserInfoResult result) {
        return new PeopleEventUserInfoResponse(result.userId(), result.userName(), result.profilePhoto());
    }
}
