package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.invite.domain.PromotionalSource;
import com.nidus.twinly.me.dto.result.MeInviteRewardResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record MeInviteRewardResponse(
        String code,
        String shareMessage,
        boolean canRegisterCode,
        int registeredCount,
        int completedCount,
        int goal,
        @Schema(nullable = true)
        MeInviteRewardRewardResponse reward,
        @Schema(nullable = true)
        PromotionalSource promotionalSource
) {

    public static MeInviteRewardResponse from(MeInviteRewardResult result) {
        return new MeInviteRewardResponse(
                result.code(),
                result.shareMessage(),
                result.canRegisterCode(),
                result.registeredCount(),
                result.completedCount(),
                result.goal(),
                result.reward() != null ? MeInviteRewardRewardResponse.from(result.reward()) : null,
                result.promotionalSource()
        );
    }
}
