package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.common.presign.RequiredHeaders;
import com.nidus.twinly.me.dto.result.MeProfilePhotoPresignResult;

import java.time.Instant;

public record MeProfilePhotoPresignResponse(
        String uploadUrl,
        String key,
        String method,
        RequiredHeaders requiredHeaders,
        Integer maxBytes,
        Instant expiresAt
) {

    public static MeProfilePhotoPresignResponse from(MeProfilePhotoPresignResult result) {
        return new MeProfilePhotoPresignResponse(result.uploadUrl(), result.key(), result.method(), result.requiredHeaders(), result.maxBytes(), result.expiresAt());
    }
}
