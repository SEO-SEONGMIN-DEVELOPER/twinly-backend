package com.nidus.twinly.legal.dto.result;

public record LegalPoliciesItemResult(
        Long policyId,
        String title,
        Integer version,
        String url,
        Boolean isRequired
) {
}
