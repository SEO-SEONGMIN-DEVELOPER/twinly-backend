package com.nidus.twinly.chat.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.chat.dto.result.ChatRoomPartnerResult;
import com.nidus.twinly.common.photo.ProfilePhotoInfo;
import io.swagger.v3.oas.annotations.media.Schema;

public record ChatRoomPartnerResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        String userName,
        @Schema(nullable = true)
        ProfilePhotoInfo profilePhoto,
        Integer intimacy,
        Boolean isDeleted
) {

    public static ChatRoomPartnerResponse from(ChatRoomPartnerResult result) {
        return new ChatRoomPartnerResponse(result.userId(), result.userName(), result.profilePhoto(), result.intimacy(), result.isDeleted());
    }
}
