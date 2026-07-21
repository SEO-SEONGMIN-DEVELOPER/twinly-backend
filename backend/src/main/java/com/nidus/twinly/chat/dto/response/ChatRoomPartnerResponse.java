package com.nidus.twinly.chat.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.chat.dto.result.ChatRoomPartnerResult;

public record ChatRoomPartnerResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        String userName,
        String profilePhotoUrl,
        Integer intimacy,
        Boolean isDeleted
) {

    public static ChatRoomPartnerResponse from(ChatRoomPartnerResult result) {
        return new ChatRoomPartnerResponse(result.userId(), result.userName(), result.profilePhotoUrl(), result.intimacy(), result.isDeleted());
    }
}
