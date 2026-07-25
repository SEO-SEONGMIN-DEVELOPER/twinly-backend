package com.nidus.twinly.me.dto.result;

import java.time.Instant;

public record MeConsentsItemResult(
        String policyId,
        String title,
        Integer version,
        String url,
        Boolean isRequired,
        Boolean isGranted,
        Instant grantedAt
) {
}
