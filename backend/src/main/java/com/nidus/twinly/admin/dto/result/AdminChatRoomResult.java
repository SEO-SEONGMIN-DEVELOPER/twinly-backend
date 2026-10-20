package com.nidus.twinly.admin.dto.result;

import com.nidus.twinly.admin.domain.ChatEntryState;
import com.nidus.twinly.admin.domain.MemberStatus;
import com.nidus.twinly.common.domain.Gender;

import java.time.Instant;
import java.util.List;

public record AdminChatRoomResult(
        Long id,
        Instant openedAt,
        String commonPoint,
        ChatEntryState entryState,
        List<Participant> participants,
        long messageCount,
        Instant lastMessageAt
) {

    public record Participant(
            Long userId,
            String nickname,
            Gender gender,
            String organization,
            MemberStatus status,
            Instant enteredAt,
            Instant leftAt
    ) {
    }
}
