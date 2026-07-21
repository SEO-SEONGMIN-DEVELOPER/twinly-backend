package com.nidus.twinly.legal.dto.response;

import com.nidus.twinly.legal.dto.result.LegalPoliciesResult;

import java.util.List;

public record LegalPoliciesResponse(
        List<LegalPoliciesItemResponse> policies
) {

    public static LegalPoliciesResponse from(LegalPoliciesResult result) {
        return new LegalPoliciesResponse(
                result.policies().stream().map(LegalPoliciesItemResponse::from).toList()
        );
    }
}
