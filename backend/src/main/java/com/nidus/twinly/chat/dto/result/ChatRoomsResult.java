package com.nidus.twinly.chat.dto.result;

import java.util.List;

public record ChatRoomsResult(
        List<ChatRoomResult> rooms
) {
}
