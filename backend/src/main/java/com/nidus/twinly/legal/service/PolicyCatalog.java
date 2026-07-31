package com.nidus.twinly.legal.service;

import com.nidus.twinly.legal.entity.PolicyName;
import com.nidus.twinly.legal.repository.PolicyNameRepository;
import com.nidus.twinly.legal.repository.PolicyRepository;
import com.nidus.twinly.legal.repository.PolicyRepository.PolicySummary;
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

    private static final Comparator<PolicySummary> BY_EFFECTIVE_AT_THEN_VERSION =
            Comparator.comparing(PolicySummary::getEffectiveAt).thenComparing(PolicySummary::getVersion);

    private final PolicyRepository policyRepository;
    private final PolicyNameRepository policyNameRepository;

    private static boolean isEffective(PolicySummary policy, Instant now) {
        return policy.getEffectiveAt() != null && !policy.getEffectiveAt().isAfter(now);
    }

    public Map<Long, PolicySummary> loadLatestByPolicyNameId(List<Long> policyNameIds) {
        Instant now = Instant.now();
        return policyRepository.findAllProjectedByPolicyNameIdIn(policyNameIds).stream()
                .filter(policy -> isEffective(policy, now))
                .collect(Collectors.toMap(
                        PolicySummary::getPolicyNameId,
                        Function.identity(),
                        BinaryOperator.maxBy(BY_EFFECTIVE_AT_THEN_VERSION)));
    }

    public Map<PolicyKey, PolicySummary> loadByKey(List<String> policyNameIdentifiers) {
        Map<Long, String> identifierByPolicyNameId = policyNameRepository.findAllByIdentifierIn(policyNameIdentifiers).stream()
                .collect(Collectors.toMap(PolicyName::getId, PolicyName::getIdentifier));

        Instant now = Instant.now();
        return policyRepository.findAllProjectedByPolicyNameIdIn(List.copyOf(identifierByPolicyNameId.keySet())).stream()
                .filter(policy -> isEffective(policy, now))
                .collect(Collectors.toMap(
                        policy -> new PolicyKey(identifierByPolicyNameId.get(policy.getPolicyNameId()), policy.getVersion()),
                        Function.identity()));
    }

    public record PolicyKey(String identifier, Integer version) {
    }
}
