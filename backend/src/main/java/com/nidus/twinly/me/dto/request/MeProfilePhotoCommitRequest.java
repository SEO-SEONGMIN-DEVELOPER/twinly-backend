package com.nidus.twinly.me.dto.request;

import com.nidus.twinly.common.photo.PhotoPosInfo;

public record MeProfilePhotoCommitRequest(
        String key,
        PhotoPosInfo position
) {
}
