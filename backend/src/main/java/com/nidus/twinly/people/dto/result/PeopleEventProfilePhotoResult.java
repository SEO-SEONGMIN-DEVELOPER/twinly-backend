package com.nidus.twinly.people.dto.result;

import com.nidus.twinly.common.photo.ProfilePhotoInfo;

public record PeopleEventProfilePhotoResult(
        Long userId,
        ProfilePhotoInfo profilePhoto
) {
}
