package com.nidus.twinly.main.dto.response;

import com.nidus.twinly.main.dto.result.MainTabInviteRewardResult;

public record MainTabInviteRewardResponse(
        String appleOfferId
) {

    public static MainTabInviteRewardResponse from(MainTabInviteRewardResult result) {
        return new MainTabInviteRewardResponse(result.appleOfferId());
    }
}
