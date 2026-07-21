package com.nidus.twinly.legal.dto.result;

import java.util.List;

public record LegalPoliciesResult(
        List<LegalPoliciesItemResult> policies
) {
}
