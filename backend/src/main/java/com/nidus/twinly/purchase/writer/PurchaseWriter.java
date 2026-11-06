package com.nidus.twinly.purchase.writer;

import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.organization.domain.OrganizationType;
import com.nidus.twinly.purchase.client.RevenueCatEntitlement;
import com.nidus.twinly.purchase.domain.PoolAllocator;
import com.nidus.twinly.purchase.domain.PoolAssignment;
import com.nidus.twinly.purchase.entity.PoolCounter;
import com.nidus.twinly.purchase.entity.RevenueCatEvent;
import com.nidus.twinly.purchase.entity.UserEntitlement;
import com.nidus.twinly.purchase.repository.PoolCounterRepository;
import com.nidus.twinly.purchase.repository.RevenueCatEventRepository;
import com.nidus.twinly.purchase.repository.UserEntitlementRepository;
import com.nidus.twinly.user.repository.UserRepository;
import com.nidus.twinly.user.repository.UserRepository.PoolCandidateProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PurchaseWriter {

    private final UserEntitlementRepository userEntitlementRepository;
    private final RevenueCatEventRepository revenueCatEventRepository;
    private final UserRepository userRepository;
    private final PoolCounterRepository poolCounterRepository;

    @Transactional
    public boolean beginEvent(String eventId, String type, Long userId, String environment, Instant receivedAt) {
        Optional<RevenueCatEvent> stored = revenueCatEventRepository.findByEventId(eventId);

        if (stored.isPresent()) {
            return stored.get().getCompletedAt() == null;
        }

        revenueCatEventRepository.save(RevenueCatEvent.receive(eventId, type, userId, environment, receivedAt));
        return true;
    }

    @Transactional
    public void completeEvent(String eventId, Instant completedAt) {
        revenueCatEventRepository.findByEventId(eventId)
                .ifPresent(event -> event.complete(completedAt));
    }

    @Transactional
    public void markSyncAttempt(Long userId, Instant attemptedAt) {
        userRepository.markPurchasesSynced(userId, attemptedAt);
    }

    @Transactional
    public void replaceEntitlements(Long userId, List<RevenueCatEntitlement> entitlements, Instant syncedAt) {
        List<UserEntitlement> stored = userEntitlementRepository.findAllByUserId(userId);

        if (stored.stream().anyMatch(entitlement -> entitlement.getSyncedAt().isAfter(syncedAt))) {
            return;
        }

        Map<String, UserEntitlement> removed = stored.stream()
                .collect(Collectors.toMap(UserEntitlement::getEntitlement, Function.identity()));

        for (RevenueCatEntitlement entitlement : entitlements) {
            UserEntitlement existing = removed.remove(entitlement.entitlement());

            if (existing == null) {
                userEntitlementRepository.save(UserEntitlement.create(userId, entitlement.entitlement(), entitlement.expiresAt(),
                        entitlement.store(), entitlement.periodType(), syncedAt));
            } else {
                existing.sync(entitlement.expiresAt(), entitlement.store(), entitlement.periodType(), syncedAt);
            }
        }

        userEntitlementRepository.deleteAll(removed.values());
    }

    @Transactional
    public void assignPool(Long userId) {
        OrganizationType organizationType = userRepository.findOrganizationTypeById(userId)
                .orElseThrow(() -> new IllegalStateException("풀을 배정할 회원이 없습니다. userId=" + userId));
        PoolCounter counter = poolCounterRepository.findWithLockByOrganizationType(organizationType)
                .orElseThrow(() -> new IllegalStateException("pool_counter 행이 없습니다. organizationType=" + organizationType));

        if (userRepository.assignPoolNumber(userId, counter.nextPoolNumber()) == 0) {
            return;
        }

        counter.increase();
    }

    public List<PoolAssignment> currentPoolAssignments() {
        Map<Integer, List<Gender>> gendersByPoolNumber = new TreeMap<>();
        userRepository.findPoolMembersOfNonSeeds().forEach(member -> gendersByPoolNumber
                .computeIfAbsent(member.getPoolNumber(), key -> new ArrayList<>()).add(member.getGender()));

        return gendersByPoolNumber.entrySet().stream()
                .map(entry -> PoolAssignment.of(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Transactional
    public List<PoolAssignment> reassignPools(List<Long> userIds) {
        List<PoolCounter> counters = poolCounterRepository.findAllWithLockByOrderByIdAsc();
        if (counters.isEmpty()) {
            throw new IllegalStateException("pool_counter 행이 없습니다.");
        }

        List<PoolCandidateProjection> candidates = userIds.isEmpty() ? List.of() : userRepository.findPoolCandidatesByIdIn(userIds);
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<Long, Gender> genderByUserId = candidates.stream()
                .collect(HashMap::new, (map, candidate) -> map.put(candidate.getId(), candidate.getGender()), HashMap::putAll);
        Map<OrganizationType, List<PoolCandidateProjection>> candidatesByType = candidates.stream()
                .collect(Collectors.groupingBy(PoolCandidateProjection::getOrganizationType));
        Map<Integer, List<Long>> userIdsByPoolNumber = new LinkedHashMap<>();
        Random random = new Random();

        for (PoolCounter counter : counters) {
            List<PoolCandidateProjection> typed = candidatesByType.getOrDefault(counter.getOrganizationType(), List.of());
            List<Long> ordered = PoolAllocator.order(
                    typed.stream().filter(candidate -> candidate.getGender() == Gender.MALE).map(PoolCandidateProjection::getId).toList(),
                    typed.stream().filter(candidate -> candidate.getGender() != Gender.MALE).map(PoolCandidateProjection::getId).toList(),
                    random);
            for (int index = 0; index < ordered.size(); index++) {
                userIdsByPoolNumber.computeIfAbsent(counter.poolNumberAt(index), key -> new ArrayList<>()).add(ordered.get(index));
            }
            counter.resetTo(ordered.size());
        }

        userRepository.clearNonSeedPoolNumbers();
        userIdsByPoolNumber.forEach((poolNumber, members) -> userRepository.updatePoolNumber(members, poolNumber));

        return userIdsByPoolNumber.entrySet().stream()
                .map(entry -> PoolAssignment.of(entry.getKey(), entry.getValue().stream().map(genderByUserId::get).toList()))
                .toList();
    }
}
