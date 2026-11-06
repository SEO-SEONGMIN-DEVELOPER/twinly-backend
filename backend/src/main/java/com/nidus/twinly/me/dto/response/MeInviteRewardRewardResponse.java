package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.invite.domain.InviteRewardMethod;
import com.nidus.twinly.invite.domain.InviteRewardStatus;
import com.nidus.twinly.invite.domain.InviteRewardWaitingReason;
import com.nidus.twinly.me.dto.result.MeInviteRewardRewardResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record MeInviteRewardRewardResponse(
        InviteRewardStatus status,
        @Schema(nullable = true)
        InviteRewardWaitingReason waitingReason,
        @Schema(nullable = true)
        InviteRewardMethod method,
        @Schema(nullable = true)
        String appleOfferId,
        @Schema(nullable = true)
        Instant endsAt
) {

    public static MeInviteRewardRewardResponse from(MeInviteRewardRewardResult result) {
        return new MeInviteRewardRewardResponse(
                result.status(),
                result.waitingReason(),
                result.method(),
                result.appleOfferId(),
                result.endsAt()
        );
    }
}
