package com.nidus.twinly.legal.dto.result;

public record LegalPoliciesItemResult(
        String policyId,
        String title,
        String version,
        String url,
        Boolean requiresAgreement,
        Boolean isRequired
) {
}
