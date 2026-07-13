package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.me.dto.request.MeProfilePhotoPresignRequest;

public record MeProfilePhotoPresignCommand(
        String contentType
) {

    public static MeProfilePhotoPresignCommand from(MeProfilePhotoPresignRequest request) {
        return new MeProfilePhotoPresignCommand(request.contentType());
    }
}
