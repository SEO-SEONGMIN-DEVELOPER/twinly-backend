package com.nidus.twinly.season.dto.result;

import com.nidus.twinly.common.photo.ProfilePhotoInfo;

public record SeasonLatestRecapClosestResult(
        Long userId,
        String userName,
        ProfilePhotoInfo profilePhoto,
        Integer twinIntimacy
) {
}
