package com.nidus.twinly.me.dto.result;

import com.nidus.twinly.common.photo.ProfilePhotoInfo;

import java.util.List;

public record MeProfileV2Result(
        Long userId,
        String userName,
        ProfilePhotoInfo profilePhoto,
        List<String> interests,
        Integer encounteredPeopleCount,
        Integer encounteredFriendCount
) {
}
