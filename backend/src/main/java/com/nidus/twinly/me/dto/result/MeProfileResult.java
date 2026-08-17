package com.nidus.twinly.me.dto.result;

import com.nidus.twinly.common.photo.ProfilePhotoInfo;

import java.util.List;

public record MeProfileResult(
        Long userId,
        String userName,
        ProfilePhotoInfo profilePhoto,
        String persona,
        List<String> interests,
        Integer encounteredPeopleCount,
        Integer encounteredFriendCount
) {
}
