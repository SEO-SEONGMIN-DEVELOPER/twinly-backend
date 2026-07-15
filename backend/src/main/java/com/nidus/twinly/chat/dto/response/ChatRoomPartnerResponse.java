package com.nidus.twinly.chat.dto.response;

import com.nidus.twinly.chat.dto.result.ChatRoomPartnerResult;

public record ChatRoomPartnerResponse(
        Long userId,
        String userName,
        String profilePhotoUrl,
        Integer rapport,
        Boolean isDeleted
) {

    public static ChatRoomPartnerResponse from(ChatRoomPartnerResult result) {
        return new ChatRoomPartnerResponse(result.userId(), result.userName(), result.profilePhotoUrl(), result.rapport(), result.isDeleted());
    }
}
