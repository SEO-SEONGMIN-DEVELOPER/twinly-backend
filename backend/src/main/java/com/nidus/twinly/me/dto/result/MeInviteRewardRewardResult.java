package com.nidus.twinly.me.dto.result;

import com.nidus.twinly.invite.domain.InviteRewardMethod;
import com.nidus.twinly.invite.domain.InviteRewardStatus;
import com.nidus.twinly.invite.domain.InviteRewardWaitingReason;

import java.time.Instant;

public record MeInviteRewardRewardResult(
        InviteRewardStatus status,
        InviteRewardWaitingReason waitingReason,
        InviteRewardMethod method,
        String appleOfferId,
        Instant endsAt
) {
}
