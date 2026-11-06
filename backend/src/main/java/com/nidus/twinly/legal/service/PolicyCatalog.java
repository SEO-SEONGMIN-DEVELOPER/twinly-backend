package com.nidus.twinly.legal.service;

import com.nidus.twinly.legal.config.LegalProperties;
import com.nidus.twinly.legal.domain.PolicyKind;
import com.nidus.twinly.legal.domain.RequiredPolicies;
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
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PolicyCatalog {

    public static final Comparator<PolicySummary> BY_EFFECTIVE_AT_THEN_ID =
            Comparator.comparing(PolicySummary::getEffectiveAt).thenComparing(PolicySummary::getId);

    private final PolicyRepository policyRepository;
    private final PolicyNameRepository policyNameRepository;
    private final LegalProperties legalProperties;

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
                        BinaryOperator.maxBy(BY_EFFECTIVE_AT_THEN_ID)));
    }

    public Set<Long> loadRequiredPolicyIds(PolicyKind kind) {
        List<Long> policyNameIds = policyNameRepository.findAllByKindAndIsDeprecatedFalseOrderByIdAsc(kind).stream()
                .filter(policyName -> Boolean.TRUE.equals(policyName.getRequiresAgreement()))
                .map(PolicyName::getId)
                .toList();

        return loadLatestByPolicyNameId(policyNameIds).values().stream()
                .filter(policy -> Boolean.TRUE.equals(policy.getIsRequired()))
                .map(PolicySummary::getId)
                .collect(Collectors.toSet());
    }

    public RequiredPolicies loadRequiredPolicies(PolicyKind kind) {
        Map<Long, String> identifierByPolicyNameId = policyNameRepository.findAllByKindAndIsDeprecatedFalseOrderByIdAsc(kind).stream()
                .filter(policyName -> Boolean.TRUE.equals(policyName.getRequiresAgreement()))
                .collect(Collectors.toMap(PolicyName::getId, PolicyName::getIdentifier));

        Instant now = Instant.now();
        Map<Long, List<PolicySummary>> versionsByPolicyNameId = policyRepository.findAllProjectedByPolicyNameIdIn(List.copyOf(identifierByPolicyNameId.keySet())).stream()
                .filter(policy -> isEffective(policy, now))
                .collect(Collectors.groupingBy(PolicySummary::getPolicyNameId));

        List<Set<Long>> acceptedPolicyIdGroups = versionsByPolicyNameId.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> acceptedPolicyIds(identifierByPolicyNameId.get(entry.getKey()), entry.getValue()))
                .filter(group -> !group.isEmpty())
                .toList();

        return new RequiredPolicies(acceptedPolicyIdGroups);
    }

    private Set<Long> acceptedPolicyIds(String identifier, List<PolicySummary> versions) {
        PolicySummary latest = versions.stream().max(BY_EFFECTIVE_AT_THEN_ID).orElseThrow();
        if (!Boolean.TRUE.equals(latest.getIsRequired())) {
            return Set.of();
        }

        String minimumVersion = legalProperties.minimumVersions().get(identifier);
        PolicySummary minimum = versions.stream()
                .filter(policy -> policy.getVersion().equals(minimumVersion))
                .findFirst()
                .orElse(latest);

        return versions.stream()
                .filter(policy -> BY_EFFECTIVE_AT_THEN_ID.compare(policy, minimum) >= 0)
                .map(PolicySummary::getId)
                .collect(Collectors.toSet());
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

    public record PolicyKey(String identifier, String version) {
    }
}
