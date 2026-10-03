package com.nidus.twinly.relationship.reader;

import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.relationship.domain.Intimacy;
import com.nidus.twinly.relationship.entity.IntimacyBonus;
import com.nidus.twinly.relationship.entity.Relationship;
import com.nidus.twinly.relationship.repository.IntimacyBonusRepository;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IntimacyReaderIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 1);
    private static final Instant AS_OF = Instant.parse("2026-10-01T09:00:00Z");

    @Autowired
    IntimacyReader intimacyReader;

    @Autowired
    RelationshipRepository relationshipRepository;

    @Autowired
    IntimacyBonusRepository intimacyBonusRepository;

    @Test
    @DisplayName("기준 시각 이후에 얻은 게임 점수만 시뮬레이션 친밀도에 더하고, 게임 몫은 지금까지 얻은 게임 점수 전부다")
    void read_adds_only_bonuses_after_intimacy_as_of() {
        // given: 09시 기준으로 계산된 친밀도 40, 기준 전 08시에 +5, 기준 후 10시와 11시에 +3씩
        User me = saveUser();
        User partner = saveUser();
        saveRelationship(me, partner, 40, AS_OF);
        saveBonus(me, partner, 5, "2026-10-01T08:00:00Z");
        saveBonus(me, partner, 3, "2026-10-01T10:00:00Z");
        saveBonus(partner, me, 3, "2026-10-01T11:00:00Z");

        // when: 현재 친밀도 조회
        Intimacy intimacy = intimacyReader.read(me.getId(), partner.getId(), KstTimes.now());

        // then: 08시 점수는 AI가 받은 40에 이미 들어 있으므로 빼고 40 + 3 + 3, 게임 몫은 5 + 3 + 3
        assertThat(intimacy).isEqualTo(new Intimacy(46, 11));
    }

    @Test
    @DisplayName("기준 시각이 없는 관계는 AI가 게임 점수를 받은 적이 없으므로 모든 게임 점수를 더한다")
    void read_adds_all_bonuses_when_intimacy_as_of_is_null() {
        // given: 기준 시각 없이 저장된 친밀도 40과 게임 점수 +5, +3
        User me = saveUser();
        User partner = saveUser();
        saveRelationship(me, partner, 40, null);
        saveBonus(me, partner, 5, "2026-10-01T08:00:00Z");
        saveBonus(me, partner, 3, "2026-10-01T10:00:00Z");

        // when: 현재 친밀도 조회
        Intimacy intimacy = intimacyReader.read(me.getId(), partner.getId(), KstTimes.now());

        // then: 40 + 5 + 3
        assertThat(intimacy).isEqualTo(new Intimacy(48, 8));
    }

    @Test
    @DisplayName("시뮬레이션 친밀도와 게임 점수의 합은 100을 넘지 않는다")
    void read_caps_intimacy_at_100() {
        // given: 친밀도 95에 기준 이후 +10
        User me = saveUser();
        User partner = saveUser();
        saveRelationship(me, partner, 95, AS_OF);
        saveBonus(me, partner, 10, "2026-10-01T10:00:00Z");

        // when: 현재 친밀도 조회
        Intimacy intimacy = intimacyReader.read(me.getId(), partner.getId(), KstTimes.now());

        // then: 105가 아니라 100
        assertThat(intimacy).isEqualTo(new Intimacy(100, 10));
    }

    @Test
    @DisplayName("게임 점수는 쌍 단위로 저장되지만, 양쪽 관계는 각자의 기준 시각으로 더한다")
    void read_applies_pair_bonus_to_each_direction_with_its_own_as_of() {
        // given: 나→상대는 09시 기준 40, 상대→나는 10시 30분 기준 50, 게임 점수는 10시와 11시에 +3씩
        User me = saveUser();
        User partner = saveUser();
        saveRelationship(me, partner, 40, AS_OF);
        saveRelationship(partner, me, 50, Instant.parse("2026-10-01T10:30:00Z"));
        saveBonus(me, partner, 3, "2026-10-01T10:00:00Z");
        saveBonus(me, partner, 3, "2026-10-01T11:00:00Z");

        // when: 양쪽에서 각각 조회
        Intimacy mine = intimacyReader.read(me.getId(), partner.getId(), KstTimes.now());
        Intimacy partners = intimacyReader.read(partner.getId(), me.getId(), KstTimes.now());

        // then: 나는 두 번 다 더하고(46), 상대는 10시 30분 이후 한 번만 더한다(53), 게임 몫은 둘 다 6
        assertThat(mine).isEqualTo(new Intimacy(46, 6));
        assertThat(partners).isEqualTo(new Intimacy(53, 6));
    }

    @Test
    @DisplayName("여러 상대를 한 번에 읽으면 상대마다 그 쌍의 게임 점수만 더하고, 점수가 없는 상대는 시뮬레이션 친밀도 그대로다")
    void readAll_sums_bonuses_per_partner() {
        // given: 첫째 상대와는 +3, 둘째 상대와는 점수 없음, 두 상대끼리 얻은 +7은 나와 무관
        User me = saveUser();
        User first = saveUser();
        User second = saveUser();
        saveRelationship(me, first, 40, AS_OF);
        saveRelationship(me, second, 20, AS_OF);
        saveBonus(me, first, 3, "2026-10-01T10:00:00Z");
        saveBonus(first, second, 7, "2026-10-01T10:00:00Z");

        // when: 두 상대를 한 번에 조회
        Map<Long, Intimacy> intimacies = intimacyReader.readAll(me.getId(), List.of(first.getId(), second.getId()), KstTimes.now());

        // then
        assertThat(intimacies).containsExactlyInAnyOrderEntriesOf(Map.of(
                first.getId(), new Intimacy(43, 3),
                second.getId(), new Intimacy(20, 0)));
    }

    @Test
    @DisplayName("갱신 시각이 아직 오지 않은 관계는 쓰지 않고, 지금 보이는 관계의 친밀도와 기준 시각으로 계산한다")
    void read_uses_latest_visible_relationship() {
        // given: 지금 보이는 관계는 09시 기준 40, 2시간 뒤에 보일 관계는 10시 30분 기준 60, 10시에 +3
        User me = saveUser();
        User partner = saveUser();
        saveRelationship(me, partner, 40, AS_OF);
        LocalDateTime later = KstTimes.now().plusHours(2);
        relationshipRepository.save(Relationship.create(me.getId(), later.toLocalDate(), "v1", partner.getId(), 60, "model",
                later, Instant.parse("2026-10-01T10:30:00Z")));
        saveBonus(me, partner, 3, "2026-10-01T10:00:00Z");

        // when: 현재 친밀도 조회
        Intimacy intimacy = intimacyReader.read(me.getId(), partner.getId(), KstTimes.now());

        // then: 미래 관계(60)가 아니라 지금 보이는 관계 기준으로 40 + 3
        assertThat(intimacy).isEqualTo(new Intimacy(43, 3));
    }

    @Test
    @DisplayName("관계가 없는 상대는 게임 점수와 상관없이 0이다")
    void read_returns_zero_without_relationship() {
        // given: 관계 없이 게임 점수만 있다
        User me = saveUser();
        User partner = saveUser();
        saveBonus(me, partner, 3, "2026-10-01T10:00:00Z");

        // when & then
        assertThat(intimacyReader.read(me.getId(), partner.getId(), KstTimes.now())).isEqualTo(Intimacy.ZERO);
        assertThat(intimacyReader.readAll(me.getId(), List.of(partner.getId()), KstTimes.now())).isEmpty();
    }

    @Test
    @DisplayName("쌍의 게임 점수 목록은 어느 쪽에서 읽어도 같고, 다른 쌍의 점수는 섞이지 않는다")
    void readBonuses_reads_pair_bonuses_in_either_order() {
        // given: 나와 상대의 +3, +4, 상대와 제3자의 +7
        User me = saveUser();
        User partner = saveUser();
        User other = saveUser();
        saveBonus(me, partner, 3, "2026-10-01T10:00:00Z");
        saveBonus(partner, me, 4, "2026-10-01T11:00:00Z");
        saveBonus(partner, other, 7, "2026-10-01T10:00:00Z");
        Relationship relationship = Relationship.create(partner.getId(), DATE, "v1", me.getId(), 40, "model", DATE.atTime(12, 0), null);

        // when: 상대 쪽에서 읽은 점수로 계산
        Intimacy intimacy = intimacyReader.readBonuses(partner.getId(), me.getId())
                .intimacyOf(relationship, Instant.parse("2026-10-02T00:00:00Z"));

        // then: 우리 쌍의 3 + 4만 더한다
        assertThat(intimacy).isEqualTo(new Intimacy(47, 7));
    }

    @Test
    @DisplayName("시뮬레이션 시작값은 그 날짜 전날까지의 마지막 기록에서 출발하고, 다시 돌리는 날짜의 기존 기록은 쓰지 않는다")
    void readForSimulation_starts_from_last_relationship_before_date() {
        // given: 9/30 마지막 30(9/29 22시 기준), 곧 지워질 10/1 v1 50(9/30 22시 기준), 게임 9/30 23시 +3, 10/1 12시 +5
        User me = saveUser();
        User partner = saveUser();
        saveRelationshipOn(me, partner, LocalDate.of(2026, 9, 30), 30, kst(2026, 9, 29, 22));
        saveRelationshipOn(me, partner, LocalDate.of(2026, 10, 1), 50, kst(2026, 9, 30, 22));
        saveBonus(me, partner, 3, kst(2026, 9, 30, 23));
        saveBonus(me, partner, 5, kst(2026, 10, 1, 12));

        // when: 10/1 15시에 10/1 을 다시 시뮬레이션하려고 시작값 조회
        Map<Long, Integer> intimacies = intimacyReader.readForSimulation(me.getId(), LocalDate.of(2026, 10, 1), kst(2026, 10, 1, 15));

        // then: 10/1 v1 의 50 이 아니라 9/30 의 30 에서 출발해 그 뒤 게임 점수를 더한다
        assertThat(intimacies).containsExactlyEntriesOf(Map.of(partner.getId(), 38));
    }

    @Test
    @DisplayName("시작값에는 기준 시각과 같은 순간의 게임 점수까지 넣고, 그 뒤의 점수는 넣지 않는다")
    void readForSimulation_includes_bonuses_up_to_as_of() {
        // given: 09시 기준 30, 기준 시각 10시 정각에 +2, 10시 1초에 +4
        User me = saveUser();
        User partner = saveUser();
        saveRelationshipOn(me, partner, LocalDate.of(2026, 9, 30), 30, AS_OF);
        saveBonus(me, partner, 2, Instant.parse("2026-10-01T10:00:00Z"));
        saveBonus(me, partner, 4, Instant.parse("2026-10-01T10:00:01Z"));

        // when: 10시 정각 기준 시작값 조회
        Map<Long, Integer> intimacies = intimacyReader.readForSimulation(me.getId(), LocalDate.of(2026, 10, 1),
                Instant.parse("2026-10-01T10:00:00Z"));

        // then: 30 + 2. 10시 1초 점수는 결과가 돌아온 뒤 기준 시각 이후 점수로 더해진다
        assertThat(intimacies).containsExactlyEntriesOf(Map.of(partner.getId(), 32));
    }

    @Test
    @DisplayName("그 날짜 전에 기록이 없는 상대는 시작값 목록에 넣지 않는다")
    void readForSimulation_excludes_partner_without_relationship_before_date() {
        // given: 첫째 상대와는 9/30 기록, 둘째 상대와는 10/1 에 처음 만난 기록만 있다
        User me = saveUser();
        User first = saveUser();
        User second = saveUser();
        saveRelationshipOn(me, first, LocalDate.of(2026, 9, 30), 30, AS_OF);
        saveRelationshipOn(me, second, LocalDate.of(2026, 10, 1), 0, AS_OF);

        // when: 10/1 시뮬레이션 시작값 조회
        Map<Long, Integer> intimacies = intimacyReader.readForSimulation(me.getId(), LocalDate.of(2026, 10, 1), Instant.now());

        // then: 둘째 상대는 처음 만나는 사이라 빠진다
        assertThat(intimacies).containsExactlyEntriesOf(Map.of(first.getId(), 30));
    }

    private void saveRelationshipOn(User user, User partner, LocalDate date, int intimacy, Instant intimacyAsOf) {
        relationshipRepository.save(Relationship.create(user.getId(), date, "v1", partner.getId(), intimacy, "model",
                date.atTime(23, 0), intimacyAsOf));
    }

    private void saveBonus(User user1, User user2, int amount, Instant createdAt) {
        IntimacyBonus bonus = IntimacyBonus.create(user1.getId(), user2.getId(), amount);
        ReflectionTestUtils.setField(bonus, "createdAt", createdAt);
        intimacyBonusRepository.save(bonus);
    }

    private Instant kst(int year, int month, int day, int hour) {
        return KstTimes.toInstant(LocalDateTime.of(year, month, day, hour, 0));
    }

    private void saveRelationship(User user, User partner, int intimacy, Instant intimacyAsOf) {
        relationshipRepository.save(Relationship.create(user.getId(), DATE, "v1", partner.getId(), intimacy, "model",
                DATE.atTime(12, 0), intimacyAsOf));
    }

    private void saveBonus(User user1, User user2, int amount, String createdAt) {
        IntimacyBonus bonus = IntimacyBonus.create(user1.getId(), user2.getId(), amount);
        ReflectionTestUtils.setField(bonus, "createdAt", Instant.parse(createdAt));
        intimacyBonusRepository.save(bonus);
    }
}
