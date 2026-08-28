package com.nidus.twinly.me.dto.result;

import java.util.List;
import java.util.UUID;

public record MePurchasesResult(
        UUID revenueCatUserId,
        List<String> entitlements
) {
}
