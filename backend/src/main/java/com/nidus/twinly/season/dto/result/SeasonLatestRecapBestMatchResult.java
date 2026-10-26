package com.nidus.twinly.season.dto.result;

import com.nidus.twinly.common.photo.ProfilePhotoInfo;

public record SeasonLatestRecapBestMatchResult(
        Long userId,
        String userName,
        ProfilePhotoInfo profilePhoto,
        Double matchRate,
        Integer comparedCount,
        Integer matchedCount
) {
}
