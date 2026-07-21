package com.nidus.twinly.legal.controller;

import com.nidus.twinly.legal.dto.response.LegalPoliciesResponse;
import com.nidus.twinly.legal.service.LegalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LegalController {

    private final LegalService legalService;

    @GetMapping("/api/v1/legal/policies")
    public LegalPoliciesResponse policies() {
        return LegalPoliciesResponse.from(legalService.policies());
    }
}
