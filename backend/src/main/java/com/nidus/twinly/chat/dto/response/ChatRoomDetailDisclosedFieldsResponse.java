package com.nidus.twinly.chat.dto.response;

import com.nidus.twinly.chat.dto.result.ChatRoomDetailDisclosedFieldsResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record ChatRoomDetailDisclosedFieldsResponse(
        @Schema(nullable = true)
        String affiliation,
        @Schema(nullable = true)
        String affiliationNumber
) {

    public static ChatRoomDetailDisclosedFieldsResponse from(ChatRoomDetailDisclosedFieldsResult result) {
        return new ChatRoomDetailDisclosedFieldsResponse(result.affiliation(), result.affiliationNumber());
    }
}
