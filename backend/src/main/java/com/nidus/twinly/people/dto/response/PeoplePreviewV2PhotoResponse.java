package com.nidus.twinly.people.dto.response;

import com.nidus.twinly.common.photo.PhotoPosInfo;
import com.nidus.twinly.people.dto.result.PeoplePreviewV2PhotoResult;

public record PeoplePreviewV2PhotoResponse(
        String photoUrl,
        PhotoPosInfo position
) {

    public static PeoplePreviewV2PhotoResponse from(PeoplePreviewV2PhotoResult result) {
        return new PeoplePreviewV2PhotoResponse(result.photoUrl(), result.position());
    }
}
