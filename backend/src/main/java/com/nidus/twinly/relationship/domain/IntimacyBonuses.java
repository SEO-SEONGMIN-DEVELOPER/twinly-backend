package com.nidus.twinly.relationship.domain;

import com.nidus.twinly.relationship.entity.IntimacyBonus;
import com.nidus.twinly.relationship.entity.Relationship;

import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;

public class IntimacyBonuses {

    private final List<IntimacyBonus> bonuses;

    public IntimacyBonuses(List<IntimacyBonus> bonuses) {
        this.bonuses = List.copyOf(bonuses);
    }

    public Intimacy intimacyOf(Relationship relationship, Instant until) {
        Instant asOf = relationship.getIntimacyAsOf();

        return Intimacy.of(
                relationship.getIntimacy(),
                sum(bonus -> bonus.getCreatedAt().isBefore(until) && (asOf == null || bonus.getCreatedAt().isAfter(asOf))),
                sum(bonus -> bonus.getCreatedAt().isBefore(until)));
    }

    private long sum(Predicate<IntimacyBonus> condition) {
        return bonuses.stream()
                .filter(condition)
                .mapToLong(IntimacyBonus::getAmount)
                .sum();
    }
}
