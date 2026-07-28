package com.nidus.twinly.activity.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.activity.dto.result.ActivityProfilePhotoResult;
import com.nidus.twinly.common.photo.ProfilePhotoInfo;
import io.swagger.v3.oas.annotations.media.Schema;

public record ActivityProfilePhotoResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        @Schema(nullable = true)
        ProfilePhotoInfo profilePhoto
) {

    public static ActivityProfilePhotoResponse from(ActivityProfilePhotoResult result) {
        return new ActivityProfilePhotoResponse(result.userId(), result.profilePhoto());
    }
}
