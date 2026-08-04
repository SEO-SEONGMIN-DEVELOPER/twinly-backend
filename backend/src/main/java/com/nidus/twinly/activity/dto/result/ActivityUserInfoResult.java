package com.nidus.twinly.activity.dto.result;

import com.nidus.twinly.common.photo.ProfilePhotoInfo;

public record ActivityUserInfoResult(
        Long userId,
        String userName,
        ProfilePhotoInfo profilePhoto
) {
}
