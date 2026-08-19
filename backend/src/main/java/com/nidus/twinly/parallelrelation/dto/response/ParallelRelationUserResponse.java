package com.nidus.twinly.parallelrelation.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.common.photo.ProfilePhotoInfo;
import com.nidus.twinly.parallelrelation.dto.result.ParallelRelationUserResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record ParallelRelationUserResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        String userName,
        @Schema(nullable = true)
        ProfilePhotoInfo profilePhoto
) {

    public static ParallelRelationUserResponse from(ParallelRelationUserResult result) {
        return new ParallelRelationUserResponse(result.userId(), result.userName(), result.profilePhoto());
    }
}
