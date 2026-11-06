package com.nidus.twinly.me.dto.result;

import com.nidus.twinly.invite.domain.PromotionalSource;

public record MeInviteRewardResult(
        String code,
        String shareMessage,
        boolean canRegisterCode,
        int registeredCount,
        int completedCount,
        int goal,
        MeInviteRewardRewardResult reward,
        PromotionalSource promotionalSource
) {
}
