package com.nidus.twinly.me.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import com.nidus.twinly.common.photo.PhotoPosInfo;

public record MeProfilePhotoCommitRequest(
        @NotNull String key,
        @NotNull @Valid PhotoPosInfo position
) {
}
