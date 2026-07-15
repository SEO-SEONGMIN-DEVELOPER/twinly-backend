package com.nidus.twinly.chat.dto.response;

import com.nidus.twinly.chat.dto.result.ChatRoomsResult;

import java.util.List;

public record ChatRoomsResponse(
        List<ChatRoomResponse> rooms
) {

    public static ChatRoomsResponse from(ChatRoomsResult result) {
        return new ChatRoomsResponse(result.rooms().stream().map(ChatRoomResponse::from).toList());
    }
}
