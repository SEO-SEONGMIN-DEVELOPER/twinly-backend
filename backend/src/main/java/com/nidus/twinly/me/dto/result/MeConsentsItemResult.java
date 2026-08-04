package com.nidus.twinly.me.dto.result;

import java.time.Instant;

public record MeConsentsItemResult(
        String policyId,
        String title,
        String version,
        String url,
        Boolean requiresAgreement,
        Boolean isRequired,
        Boolean isGranted,
        Instant grantedAt
) {
}
