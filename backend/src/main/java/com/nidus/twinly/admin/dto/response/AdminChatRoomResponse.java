package com.nidus.twinly.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.admin.domain.ChatEntryState;
import com.nidus.twinly.admin.domain.MemberStatus;
import com.nidus.twinly.admin.dto.result.AdminChatRoomResult;
import com.nidus.twinly.common.domain.Gender;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public record AdminChatRoomResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long id,
        Instant openedAt,
        @Schema(nullable = true, description = "AI가 찾은 두 회원의 공통점")
        String commonPoint,
        @Schema(description = "BOTH: 둘 다 입장, ONE: 한 명만 입장, NONE: 미입장")
        ChatEntryState entryState,
        List<Participant> participants,
        Long messageCount,
        @Schema(nullable = true)
        Instant lastMessageAt
) {

    public static AdminChatRoomResponse from(AdminChatRoomResult result) {
        return new AdminChatRoomResponse(
                result.id(),
                result.openedAt(),
                result.commonPoint(),
                result.entryState(),
                result.participants().stream().map(Participant::from).toList(),
                result.messageCount(),
                result.lastMessageAt()
        );
    }

    public record Participant(
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            Long userId,
            @Schema(nullable = true)
            String nickname,
            @Schema(nullable = true)
            Gender gender,
            @Schema(nullable = true)
            String organization,
            @Schema(nullable = true)
            MemberStatus status,
            @Schema(nullable = true)
            Instant enteredAt,
            @Schema(nullable = true)
            Instant leftAt
    ) {

        static Participant from(AdminChatRoomResult.Participant result) {
            return new Participant(result.userId(), result.nickname(), result.gender(), result.organization(),
                    result.status(), result.enteredAt(), result.leftAt());
        }
    }
}
