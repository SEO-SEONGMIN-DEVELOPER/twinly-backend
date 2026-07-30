package com.nidus.twinly.legal.service;

import com.nidus.twinly.legal.dto.result.LegalPoliciesItemResult;
import com.nidus.twinly.legal.dto.result.LegalPoliciesResult;
import com.nidus.twinly.legal.entity.Policy;
import com.nidus.twinly.legal.entity.PolicyName;
import com.nidus.twinly.legal.repository.PolicyNameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LegalService {

    private final PolicyNameRepository policyNameRepository;
    private final PolicyCatalog policyCatalog;

    public LegalPoliciesResult policies() {
        List<PolicyName> policyNames = policyNameRepository.findAllByIsDeprecatedFalse();
        List<Long> policyNameIds = policyNames.stream().map(PolicyName::getId).toList();

        Map<Long, Policy> latestByPolicyNameId = policyCatalog.loadLatestByPolicyNameId(policyNameIds);

        List<LegalPoliciesItemResult> policies = policyNames.stream()
                .map(policyName -> {
                    Policy latest = latestByPolicyNameId.get(policyName.getId());
                    return new LegalPoliciesItemResult(
                            policyName.getIdentifier(),
                            policyName.getName(),
                            latest != null ? latest.getVersion() : null,
                            latest != null ? latest.getUrl() : null,
                            latest != null ? latest.getIsRequired() : null);
                })
                .toList();

        return new LegalPoliciesResult(policies);
    }
}
