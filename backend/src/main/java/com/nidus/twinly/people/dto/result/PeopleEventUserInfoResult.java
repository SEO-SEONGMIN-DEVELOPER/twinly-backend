package com.nidus.twinly.people.dto.result;

import com.nidus.twinly.common.photo.ProfilePhotoInfo;

public record PeopleEventUserInfoResult(
        Long userId,
        String userName,
        ProfilePhotoInfo profilePhoto
) {
}
