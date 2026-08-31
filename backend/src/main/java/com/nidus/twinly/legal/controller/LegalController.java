package com.nidus.twinly.legal.controller;

import com.nidus.twinly.legal.dto.response.LegalPoliciesResponse;
import com.nidus.twinly.legal.domain.PolicyKind;
import com.nidus.twinly.legal.service.LegalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "약관")
@RestController
@RequiredArgsConstructor
public class LegalController {

    private final LegalService legalService;

    @Operation(summary = "약관 목록 조회")
    @GetMapping("/api/v1/legal/policies")
    public LegalPoliciesResponse policies(@RequestParam(defaultValue = "onboarding") PolicyKind kind) {
        return LegalPoliciesResponse.from(legalService.policies(kind));
    }
}
