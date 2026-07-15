package com.nidus.twinly.chat.dto.result;

public record ChatRoomPartnerResult(
        Long userId,
        String userName,
        String profilePhotoUrl,
        Integer rapport,
        Boolean isDeleted
) {
}
