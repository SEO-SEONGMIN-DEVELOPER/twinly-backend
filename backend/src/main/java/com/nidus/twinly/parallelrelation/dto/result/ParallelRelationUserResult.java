package com.nidus.twinly.parallelrelation.dto.result;

import com.nidus.twinly.common.photo.ProfilePhotoInfo;

public record ParallelRelationUserResult(
        Long userId,
        String userName,
        ProfilePhotoInfo profilePhoto
) {
}
