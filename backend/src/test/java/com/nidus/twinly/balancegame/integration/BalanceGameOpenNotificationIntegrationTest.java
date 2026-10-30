package com.nidus.twinly.balancegame.integration;

import com.nidus.twinly.block.entity.Block;
import com.nidus.twinly.block.repository.BlockRepository;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.relationship.entity.Relationship;
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

import static org.assertj.core.api.Assertions.assertThat;

class BalanceGameOpenNotificationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    RelationshipRepository relationshipRepository;

    @Autowired
    BlockRepository blockRepository;

    @Test
    @DisplayName("출제 알림 대상은 지금 사람 목록에 트윈이 한 명이라도 보이는 실사용자이고, 트윈이 여럿이어도 한 번만 나온다")
    void targets_real_users_with_visible_partner_once() {
        // given: me 는 트윈 둘이 보인다
        LocalDateTime now = KstTimes.now();
        User me = saveUser();
        User partner = saveUser();
        User other = saveUser();
        relate(me, partner, now.minusHours(1));
        relate(me, other, now.minusHours(1));

        // when
        List<Long> userIds = relationshipRepository.findUserIdsWithVisiblePartner(null, LocalDate.EPOCH, now);

        // then: me 는 한 번, 관계 기록이 없는 partner·other 는 대상이 아니다
        assertThat(userIds).containsOnlyOnce(me.getId());
        assertThat(userIds).doesNotContain(partner.getId(), other.getId());
    }

    @Test
    @DisplayName("아직 공개 전인 관계만 있거나, 시드·탈퇴 신청 유저거나, 트윈이 모두 탈퇴했으면 대상이 아니다")
    void excludes_users_without_playable_partner() {
        // given
        LocalDateTime now = KstTimes.now();
        User partner = saveUser();

        User notYetVisible = saveUser();
        relate(notYetVisible, partner, now.plusHours(1));

        User seed = saveUser();
        ReflectionTestUtils.setField(seed, "isSeed", true);
        userRepository.save(seed);
        relate(seed, partner, now.minusHours(1));

        User withdrawing = saveUser();
        ReflectionTestUtils.setField(withdrawing, "withdrawalRequestedAt", Instant.now());
        userRepository.save(withdrawing);
        relate(withdrawing, partner, now.minusHours(1));

        User withdrawnPartner = saveUser();
        ReflectionTestUtils.setField(withdrawnPartner, "withdrawalRequestedAt", Instant.now());
        userRepository.save(withdrawnPartner);
        User leftAlone = saveUser();
        relate(leftAlone, withdrawnPartner, now.minusHours(1));

        // when
        List<Long> userIds = relationshipRepository.findUserIdsWithVisiblePartner(null, LocalDate.EPOCH, now);

        // then
        assertThat(userIds).doesNotContain(notYetVisible.getId(), seed.getId(), withdrawing.getId(), leftAlone.getId());
    }

    @Test
    @DisplayName("유일한 트윈과 어느 쪽에서든 차단으로 끊겼으면 대상이 아니고, 다른 트윈이 남아 있으면 대상이다")
    void excludes_users_whose_only_partner_is_blocked_either_way() {
        // given: blocker 가 blocked 를 차단했고, 둘은 서로가 유일한 트윈이다
        LocalDateTime now = KstTimes.now();
        User blocker = saveUser();
        User blocked = saveUser();
        relate(blocker, blocked, now.minusHours(1));
        relate(blocked, blocker, now.minusHours(1));
        blockRepository.save(Block.create(blocker.getId(), blocked.getId()));

        // given: stillPlaying 도 blocked 를 차단했지만 다른 트윈이 하나 더 있다
        User stillPlaying = saveUser();
        User remaining = saveUser();
        relate(stillPlaying, blocked, now.minusHours(1));
        relate(stillPlaying, remaining, now.minusHours(1));
        blockRepository.save(Block.create(stillPlaying.getId(), blocked.getId()));

        // when
        List<Long> userIds = relationshipRepository.findUserIdsWithVisiblePartner(null, LocalDate.EPOCH, now);

        // then
        assertThat(userIds).doesNotContain(blocker.getId(), blocked.getId());
        assertThat(userIds).contains(stillPlaying.getId());
    }

    @Test
    @DisplayName("이번 시즌 인연이 없는 유저는 출제 알림 대상이 아니다")
    void findUserIdsWithVisiblePartner_excludes_previous_season_only_user() {
        // given: 시즌 시작 전 회차의 관계만 있는 유저
        LocalDateTime now = LocalDateTime.of(2026, 11, 2, 12, 0);
        User user = saveUser();
        User partner = saveUser();
        relationshipRepository.save(Relationship.create(user.getId(), LocalDate.of(2026, 10, 31), "v1", partner.getId(), 40, "model",
                LocalDateTime.of(2026, 10, 31, 12, 0), null));

        // when: 11-01 시작 시즌 기준으로 조회
        List<Long> userIds = relationshipRepository.findUserIdsWithVisiblePartner(null, LocalDate.of(2026, 11, 1), now);

        // then
        assertThat(userIds).doesNotContain(user.getId());
    }

    private void relate(User user, User partner, LocalDateTime updateTime) {
        relationshipRepository.save(Relationship.create(user.getId(), updateTime.toLocalDate(), "v1", partner.getId(), 40,
                "model", updateTime, null));
    }
}
