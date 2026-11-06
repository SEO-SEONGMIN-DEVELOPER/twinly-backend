package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MeIssueInviteRewardAppleOfferSignatureResult;

import java.util.UUID;

public record MeIssueInviteRewardAppleOfferSignatureResponse(
        String productId,
        String offerId,
        String keyId,
        UUID nonce,
        long timestamp,
        UUID appAccountToken,
        String signature
) {

    public static MeIssueInviteRewardAppleOfferSignatureResponse from(MeIssueInviteRewardAppleOfferSignatureResult result) {
        return new MeIssueInviteRewardAppleOfferSignatureResponse(
                result.productId(),
                result.offerId(),
                result.keyId(),
                result.nonce(),
                result.timestamp(),
                result.appAccountToken(),
                result.signature()
        );
    }
}
