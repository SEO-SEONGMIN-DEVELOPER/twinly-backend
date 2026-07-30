package com.nidus.twinly.legal.service;

import com.nidus.twinly.legal.entity.Policy;
import com.nidus.twinly.legal.entity.PolicyName;
import com.nidus.twinly.legal.repository.PolicyNameRepository;
import com.nidus.twinly.legal.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PolicyCatalog {

    private static final Comparator<Policy> BY_EFFECTIVE_AT_THEN_VERSION =
            Comparator.comparing(Policy::getEffectiveAt).thenComparing(Policy::getVersion);

    private final PolicyRepository policyRepository;
    private final PolicyNameRepository policyNameRepository;

    private static boolean isEffective(Policy policy, Instant now) {
        return policy.getEffectiveAt() != null && !policy.getEffectiveAt().isAfter(now);
    }

    public Map<Long, Policy> loadLatestByPolicyNameId(List<Long> policyNameIds) {
        Instant now = Instant.now();
        return policyRepository.findAllByPolicyNameIdIn(policyNameIds).stream()
                .filter(policy -> isEffective(policy, now))
                .collect(Collectors.toMap(
                        Policy::getPolicyNameId,
                        Function.identity(),
                        BinaryOperator.maxBy(BY_EFFECTIVE_AT_THEN_VERSION)));
    }

    public Map<PolicyKey, Policy> loadByKey(List<String> policyNameIdentifiers) {
        Map<Long, String> identifierByPolicyNameId = policyNameRepository.findAllByIdentifierIn(policyNameIdentifiers).stream()
                .collect(Collectors.toMap(PolicyName::getId, PolicyName::getIdentifier));

        Instant now = Instant.now();
        return policyRepository.findAllByPolicyNameIdIn(List.copyOf(identifierByPolicyNameId.keySet())).stream()
                .filter(policy -> isEffective(policy, now))
                .collect(Collectors.toMap(
                        policy -> new PolicyKey(identifierByPolicyNameId.get(policy.getPolicyNameId()), policy.getVersion()),
                        Function.identity()));
    }

    public record PolicyKey(String identifier, Integer version) {
    }
}
