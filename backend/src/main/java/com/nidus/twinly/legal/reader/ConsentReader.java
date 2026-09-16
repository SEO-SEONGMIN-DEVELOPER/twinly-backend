package com.nidus.twinly.legal.reader;

import com.nidus.twinly.legal.domain.PolicyKind;
import com.nidus.twinly.legal.entity.Agreement;
import com.nidus.twinly.legal.repository.AgreementRepository;
import com.nidus.twinly.legal.service.PolicyCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ConsentReader {

    private final PolicyCatalog policyCatalog;
    private final AgreementRepository agreementRepository;

    public boolean hasAgreedAllRequired(Long userId, PolicyKind kind) {
        Set<Long> agreedPolicyIds = agreementRepository.findAllByUserIdAndRevokedAtIsNull(userId).stream()
                .map(Agreement::getPolicyId)
                .collect(Collectors.toSet());

        return agreedPolicyIds.containsAll(policyCatalog.loadRequiredPolicyIds(kind));
    }

    public List<Long> filterAgreedAllRequired(List<Long> userIds, PolicyKind kind) {
        if (userIds.isEmpty()) {
            return List.of();
        }

        Set<Long> requiredPolicyIds = policyCatalog.loadRequiredPolicyIds(kind);

        Map<Long, Set<Long>> agreedPolicyIdsByUserId = agreementRepository.findAllByUserIdInAndRevokedAtIsNull(userIds).stream()
                .collect(Collectors.groupingBy(Agreement::getUserId, Collectors.mapping(Agreement::getPolicyId, Collectors.toSet())));

        return userIds.stream()
                .filter(userId -> agreedPolicyIdsByUserId.getOrDefault(userId, Set.of()).containsAll(requiredPolicyIds))
                .toList();
    }
}
