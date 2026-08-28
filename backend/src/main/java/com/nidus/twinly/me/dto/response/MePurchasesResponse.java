package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MePurchasesResult;

import java.util.List;
import java.util.UUID;

public record MePurchasesResponse(
        UUID revenueCatUserId,
        List<String> entitlements
) {

    public static MePurchasesResponse from(MePurchasesResult result) {
        return new MePurchasesResponse(
                result.revenueCatUserId(),
                result.entitlements()
        );
    }
}
