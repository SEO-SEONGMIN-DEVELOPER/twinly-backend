package com.nidus.twinly.legal.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.legal.dto.result.LegalPoliciesItemResult;

public record LegalPoliciesItemResponse(
        String policyId,
        String title,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Integer version,
        String url,
        Boolean isRequired
) {

    public static LegalPoliciesItemResponse from(LegalPoliciesItemResult result) {
        return new LegalPoliciesItemResponse(
                result.policyId(),
                result.title(),
                result.version(),
                result.url(),
                result.isRequired()
        );
    }
}
