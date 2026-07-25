package com.nidus.twinly.legal.service;

import com.nidus.twinly.legal.entity.Policy;
import com.nidus.twinly.legal.entity.PolicyName;
import com.nidus.twinly.legal.repository.PolicyNameRepository;
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
    private final PolicyNameRepository policyNameRepository;

    public Map<PolicyKey, Policy> loadByKey(List<String> policyNameIdentifiers) {
        Map<Long, String> identifierByPolicyNameId = policyNameRepository.findAllByIdentifierIn(policyNameIdentifiers).stream()
                .collect(Collectors.toMap(PolicyName::getId, PolicyName::getIdentifier));

        return policyRepository.findAllByPolicyNameIdIn(List.copyOf(identifierByPolicyNameId.keySet())).stream()
                .collect(Collectors.toMap(
                        policy -> new PolicyKey(identifierByPolicyNameId.get(policy.getPolicyNameId()), policy.getVersion()),
                        Function.identity()));
    }

    public record PolicyKey(String identifier, Integer version) {
    }
}
