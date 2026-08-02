package com.nidus.twinly.chat.dto.result;

import java.util.List;

public record ChatMessagesResult(
        Long roomId,
        List<ChatMessageItemResult> messages,
        Long lastReadMessageId,
        ChatMessagesPageResult page
) {
}
