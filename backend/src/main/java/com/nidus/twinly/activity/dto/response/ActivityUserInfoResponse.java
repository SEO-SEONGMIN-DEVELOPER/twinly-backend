package com.nidus.twinly.activity.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.activity.dto.result.ActivityUserInfoResult;
import com.nidus.twinly.common.photo.ProfilePhotoInfo;
import io.swagger.v3.oas.annotations.media.Schema;

public record ActivityUserInfoResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        String userName,
        @Schema(nullable = true)
        ProfilePhotoInfo profilePhoto
) {

    public static ActivityUserInfoResponse from(ActivityUserInfoResult result) {
        return new ActivityUserInfoResponse(result.userId(), result.userName(), result.profilePhoto());
    }
}
