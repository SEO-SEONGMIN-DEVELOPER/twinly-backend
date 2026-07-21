package com.nidus.twinly.chat.dto.response;

import com.nidus.twinly.chat.dto.result.ChatRoomDetailDisclosedFieldsResult;

public record ChatRoomDetailDisclosedFieldsResponse(
        String affiliation,
        String affiliationNumber
) {

    public static ChatRoomDetailDisclosedFieldsResponse from(ChatRoomDetailDisclosedFieldsResult result) {
        return new ChatRoomDetailDisclosedFieldsResponse(result.affiliation(), result.affiliationNumber());
    }
}
