package com.nidus.twinly.people.dto.result;

import com.nidus.twinly.common.photo.PhotoPosInfo;

public record PeoplePreviewV2PhotoResult(
        String photoUrl,
        PhotoPosInfo position
) {
}
