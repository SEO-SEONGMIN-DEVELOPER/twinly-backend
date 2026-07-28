package com.nidus.twinly.activity.dto.result;

import com.nidus.twinly.common.photo.ProfilePhotoInfo;

public record ActivityProfilePhotoResult(
        Long userId,
        ProfilePhotoInfo profilePhoto
) {
}
