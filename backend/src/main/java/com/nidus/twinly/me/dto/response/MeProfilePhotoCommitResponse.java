package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.common.photo.PhotoPosInfo;
import com.nidus.twinly.me.dto.result.MeProfilePhotoCommitResult;

public record MeProfilePhotoCommitResponse(
        String photoUrl,
        PhotoPosInfo position
) {

    public static MeProfilePhotoCommitResponse from(MeProfilePhotoCommitResult result) {
        return new MeProfilePhotoCommitResponse(result.photoUrl(), result.position());
    }
}
