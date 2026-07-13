package com.nidus.twinly.me.dto.result;

import com.nidus.twinly.common.photo.PhotoPosInfo;

public record MeProfilePhotoCommitResult(
        String photoUrl,
        PhotoPosInfo position
) {
}
