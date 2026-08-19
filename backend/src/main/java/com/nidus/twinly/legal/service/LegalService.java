package com.nidus.twinly.legal.service;

import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.legal.dto.result.LegalPoliciesItemResult;
import com.nidus.twinly.legal.dto.result.LegalPoliciesResult;
import com.nidus.twinly.legal.domain.PolicyKind;
import com.nidus.twinly.legal.repository.PolicyRepository.PolicySummary;
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
    private final CloudFrontService cloudFrontService;

    public LegalPoliciesResult policies(PolicyKind kind) {
        List<PolicyName> policyNames = policyNameRepository.findAllByKindAndIsDeprecatedFalseOrderByIdAsc(kind);
        List<Long> policyNameIds = policyNames.stream().map(PolicyName::getId).toList();

        Map<Long, PolicySummary> latestByPolicyNameId = policyCatalog.loadLatestByPolicyNameId(policyNameIds);

        List<LegalPoliciesItemResult> policies = policyNames.stream()
                .map(policyName -> {
                    PolicySummary latest = latestByPolicyNameId.get(policyName.getId());
                    return new LegalPoliciesItemResult(
                            policyName.getIdentifier(),
                            policyName.getName(),
                            latest != null ? latest.getVersion() : null,
                            latest != null ? cloudFrontService.getPublicUrl(latest.getKey()) : null,
                            policyName.getRequiresAgreement(),
                            latest != null ? latest.getIsRequired() : null);
                })
                .toList();

        return new LegalPoliciesResult(policies);
    }
}
