package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.common.photo.PhotoPosInfo;
import com.nidus.twinly.me.dto.request.MeProfilePhotoCommitRequest;

public record MeProfilePhotoCommitCommand(
        String key,
        PhotoPosInfo position
) {

    public static MeProfilePhotoCommitCommand from(MeProfilePhotoCommitRequest request) {
        return new MeProfilePhotoCommitCommand(request.key(), request.position());
    }
}
