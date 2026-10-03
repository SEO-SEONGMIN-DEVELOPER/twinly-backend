package com.nidus.twinly.relationship.reader;

import com.nidus.twinly.relationship.domain.Intimacy;
import com.nidus.twinly.relationship.domain.IntimacyBonuses;
import com.nidus.twinly.relationship.entity.Relationship;
import com.nidus.twinly.relationship.repository.IntimacyBonusRepository;
import com.nidus.twinly.relationship.repository.IntimacyBonusRepository.BonusSumProjection;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class IntimacyReader {

    private final RelationshipRepository relationshipRepository;
    private final IntimacyBonusRepository intimacyBonusRepository;

    public Intimacy read(Long userId, Long partnerUserId, LocalDateTime now) {
        return relationshipRepository.findLatestUntilByUserIdAndPartnerUserId(userId, partnerUserId, now)
                .map(relationship -> withBonus(List.of(relationship), Instant.now()).get(partnerUserId))
                .orElse(Intimacy.ZERO);
    }

    public Map<Long, Intimacy> readAll(Long userId, List<Long> partnerUserIds, LocalDateTime now) {
        if (partnerUserIds.isEmpty()) {
            return Map.of();
        }

        return withBonus(relationshipRepository.findLatestUntilByUserIdAndPartnerUserIdIn(userId, partnerUserIds, now), Instant.now());
    }

    public Map<Long, Integer> readForSimulation(Long userId, LocalDate date, Instant asOf) {
        return withBonus(relationshipRepository.findLatestBeforeDateByUserId(userId, date), asOf).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().value()));
    }

    public IntimacyBonuses readBonuses(Long userId, Long partnerUserId) {
        return new IntimacyBonuses(intimacyBonusRepository.findAllByUserAIdAndUserBId(
                Math.min(userId, partnerUserId), Math.max(userId, partnerUserId)));
    }

    private Map<Long, Intimacy> withBonus(List<Relationship> relationships, Instant until) {
        if (relationships.isEmpty()) {
            return Map.of();
        }

        Map<Long, BonusSumProjection> sumByRelationshipId = intimacyBonusRepository
                .sumByRelationshipIdIn(relationships.stream().map(Relationship::getId).toList(), until).stream()
                .collect(Collectors.toMap(BonusSumProjection::getRelationshipId, Function.identity()));

        return relationships.stream()
                .collect(Collectors.toMap(Relationship::getPartnerUserId,
                        relationship -> intimacyOf(relationship, sumByRelationshipId.get(relationship.getId()))));
    }

    private Intimacy intimacyOf(Relationship relationship, BonusSumProjection sum) {
        if (sum == null) {
            return Intimacy.of(relationship.getIntimacy(), 0, 0);
        }

        return Intimacy.of(relationship.getIntimacy(), sum.getAmountAfterAsOf(), sum.getTotalAmount());
    }
}
