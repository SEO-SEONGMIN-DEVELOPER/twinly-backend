package com.nidus.twinly.relationship.domain;

import com.nidus.twinly.relationship.entity.IntimacyBonus;
import com.nidus.twinly.relationship.entity.Relationship;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IntimacyBonusesUnitTest {

    private static final Instant AS_OF = Instant.parse("2026-10-01T09:00:00Z");

    private final IntimacyBonuses bonuses = new IntimacyBonuses(List.of(
            bonus(5, "2026-10-01T08:00:00Z"),
            bonus(3, "2026-10-01T10:00:00Z"),
            bonus(4, "2026-10-02T10:00:00Z")));

    @Test
    @DisplayName("기준 시각 이후이면서 그 시점 전에 얻은 게임 점수만 더하고, 게임 몫은 그 시점 전 점수 전부다")
    void intimacyOf_counts_bonuses_between_as_of_and_until() {
        // given: 09시 기준 친밀도 40
        Relationship relationship = relationship(40, AS_OF);

        // when: 10월 1일이 끝나는 시점의 친밀도
        Intimacy intimacy = bonuses.intimacyOf(relationship, Instant.parse("2026-10-02T00:00:00Z"));

        // then: 08시(기준 전)와 10월 2일(그 시점 이후)은 빼고 40 + 3, 게임 몫은 5 + 3
        assertThat(intimacy).isEqualTo(new Intimacy(43, 8));
    }

    @Test
    @DisplayName("기준 시각이 없는 관계는 그 시점 전 게임 점수를 모두 더한다")
    void intimacyOf_counts_all_bonuses_before_until_without_as_of() {
        // given: 기준 시각 없는 친밀도 40
        Relationship relationship = relationship(40, null);

        // when: 10월 2일이 끝나는 시점의 친밀도
        Intimacy intimacy = bonuses.intimacyOf(relationship, Instant.parse("2026-10-03T00:00:00Z"));

        // then: 40 + 5 + 3 + 4
        assertThat(intimacy).isEqualTo(new Intimacy(52, 12));
    }

    @Test
    @DisplayName("기준 시각과 같은 순간에 얻은 점수는 AI가 받은 친밀도에 이미 들어 있다고 보고 더하지 않는다")
    void intimacyOf_excludes_bonus_at_exact_as_of() {
        // given: 기준 시각과 같은 순간의 +2
        IntimacyBonuses atAsOf = new IntimacyBonuses(List.of(bonus(2, "2026-10-01T09:00:00Z")));

        // when
        Intimacy intimacy = atAsOf.intimacyOf(relationship(40, AS_OF), Instant.parse("2026-10-02T00:00:00Z"));

        // then: 40 그대로, 게임 몫에는 들어간다
        assertThat(intimacy).isEqualTo(new Intimacy(40, 2));
    }

    @Test
    @DisplayName("행과 시즌 id 가 같은 게임 점수만 값과 게임 몫에 더한다")
    void intimacyOf_counts_only_bonuses_of_same_season() {
        // given: 시즌 2 행, 시즌 1·2·없음 점수
        Relationship relationship = Relationship.create(1L, LocalDate.of(2026, 10, 1), "v1", 2L, 40, "model", null, AS_OF, 2L);
        IntimacyBonus previous = IntimacyBonus.create(1L, 2L, 7, 1L);
        IntimacyBonus current = IntimacyBonus.create(1L, 2L, 3, 2L);
        IntimacyBonus unknown = IntimacyBonus.create(1L, 2L, 5);
        ReflectionTestUtils.setField(previous, "createdAt", Instant.parse("2026-10-01T10:00:00Z"));
        ReflectionTestUtils.setField(current, "createdAt", Instant.parse("2026-10-01T10:00:00Z"));
        ReflectionTestUtils.setField(unknown, "createdAt", Instant.parse("2026-10-01T10:00:00Z"));

        // when
        Intimacy intimacy = new IntimacyBonuses(List.of(previous, current, unknown)).intimacyOf(relationship, Instant.parse("2026-10-02T00:00:00Z"));

        // then: 시즌 2 점수 3 만 더해진다
        assertThat(intimacy).isEqualTo(new Intimacy(43, 3));
    }

    private static Relationship relationship(int intimacy, Instant intimacyAsOf) {
        return Relationship.create(1L, LocalDate.of(2026, 10, 1), "v1", 2L, intimacy, "model", null, intimacyAsOf);
    }

    private static IntimacyBonus bonus(int amount, String createdAt) {
        IntimacyBonus bonus = IntimacyBonus.create(1L, 2L, amount);
        ReflectionTestUtils.setField(bonus, "createdAt", Instant.parse(createdAt));
        return bonus;
    }
}
