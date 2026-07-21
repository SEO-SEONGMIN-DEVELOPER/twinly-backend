package com.nidus.twinly.legal.service;

import com.nidus.twinly.legal.dto.result.LegalPoliciesItemResult;
import com.nidus.twinly.legal.dto.result.LegalPoliciesResult;
import com.nidus.twinly.legal.entity.Policy;
import com.nidus.twinly.legal.entity.PolicyName;
import com.nidus.twinly.legal.repository.PolicyNameRepository;
import com.nidus.twinly.legal.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LegalService {

    private final PolicyNameRepository policyNameRepository;
    private final PolicyRepository policyRepository;

    public LegalPoliciesResult policies() {
        List<PolicyName> policyNames = policyNameRepository.findAllByIsDeprecatedFalse();
        List<Long> policyNameIds = policyNames.stream().map(PolicyName::getId).toList();

        Instant now = Instant.now();
        Map<Long, Policy> latestByPolicyNameId = policyRepository.findAllByPolicyNameIdIn(policyNameIds).stream()
                .filter(policy -> policy.getEffectiveAt() != null && !policy.getEffectiveAt().isAfter(now))
                .collect(Collectors.toMap(
                        Policy::getPolicyNameId,
                        Function.identity(),
                        (a, b) -> a.getEffectiveAt().isAfter(b.getEffectiveAt()) ? a : b));

        List<LegalPoliciesItemResult> policies = policyNames.stream()
                .map(policyName -> {
                    Policy latest = latestByPolicyNameId.get(policyName.getId());
                    return new LegalPoliciesItemResult(
                            policyName.getId(),
                            policyName.getName(),
                            latest != null ? latest.getVersion() : null,
                            latest != null ? latest.getUrl() : null,
                            latest != null ? latest.getIsRequired() : null);
                })
                .toList();

        return new LegalPoliciesResult(policies);
    }
}
