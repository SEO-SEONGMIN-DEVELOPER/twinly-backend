package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.me.dto.request.MeAcceptInviteRewardAppleOfferRequest;

public record MeAcceptInviteRewardAppleOfferCommand(
        String transactionId
) {

    public static MeAcceptInviteRewardAppleOfferCommand from(MeAcceptInviteRewardAppleOfferRequest request) {
        return new MeAcceptInviteRewardAppleOfferCommand(request.transactionId());
    }
}
