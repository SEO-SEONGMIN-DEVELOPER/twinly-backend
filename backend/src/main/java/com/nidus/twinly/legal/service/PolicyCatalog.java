package com.nidus.twinly.legal.service;

import com.nidus.twinly.legal.entity.Policy;
import com.nidus.twinly.legal.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PolicyCatalog {

    private final PolicyRepository policyRepository;

    public Map<PolicyKey, Policy> loadByKey(List<Long> policyNameIds) {
        return policyRepository.findAllByPolicyNameIdIn(policyNameIds).stream()
                .collect(Collectors.toMap(
                        policy -> new PolicyKey(policy.getPolicyNameId(), policy.getVersion()),
                        Function.identity()));
    }

    public record PolicyKey(Long policyNameId, Integer version) {
    }
}
