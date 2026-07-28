package com.nidus.twinly.common.photo;

public record ProfilePhotoInfo(
        String key,
        String photoUrl,
        PhotoPosInfo position
) {
}
