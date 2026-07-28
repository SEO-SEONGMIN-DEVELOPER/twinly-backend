package com.nidus.twinly.chat.dto.result;

import com.nidus.twinly.common.photo.ProfilePhotoInfo;

public record ChatRoomPartnerResult(
        Long userId,
        String userName,
        ProfilePhotoInfo profilePhoto,
        Integer intimacy,
        Boolean isDeleted
) {
}
