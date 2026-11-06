package com.nidus.twinly.invite.domain;

import com.nidus.twinly.purchase.client.RevenueCatSubscription;
import com.nidus.twinly.purchase.client.RevenueCatSubscriptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InviteRewardJudgeUnitTest {

    private static final Instant NOW = Instant.parse("2026-11-04T00:00:00Z");

    @Test
    @DisplayName("구독도 권한도 없으면 지금부터 30일 무료 지급이다")
    void judge_without_anything_is_promotional_from_now() {
        // when: 아무것도 없는 유저 판정
        RewardDecision decision = InviteRewardJudge.judge(new RevenueCatSubscriptions(null, List.of()), NOW);

        // then: 지금 기준 무료 지급
        assertThat(decision.kind()).isEqualTo(RewardDecision.Kind.PROMOTIONAL);
        assertThat(decision.targetExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
    }

    @Test
    @DisplayName("결제 유예 중이면 billingIssue 로 기다린다")
    void judge_grace_period_waits_for_billing_issue() {
        // given: 만료됐지만 유예 기간이 남은 Play 구독
        RevenueCatSubscription inGrace = new RevenueCatSubscription("twinly_monthly:monthly", RevenueCatSubscription.PLAY_STORE, "normal",
                NOW.minus(Duration.ofDays(31)), NOW.minus(Duration.ofDays(1)), null, NOW.minus(Duration.ofDays(1)), NOW.plus(Duration.ofDays(2)));

        // when: 판정
        RewardDecision decision = InviteRewardJudge.judge(new RevenueCatSubscriptions(null, List.of(inGrace)), NOW);

        // then: billingIssue 대기
        assertThat(decision.kind()).isEqualTo(RewardDecision.Kind.WAIT_BILLING_ISSUE);
    }

    @Test
    @DisplayName("결제 문제로 끝난 지 오래된 구독은 더 기다리지 않고 무료 지급한다")
    void judge_long_expired_billing_issue_is_promotional() {
        // given: 90일 전에 결제 문제로 만료된 구독
        RevenueCatSubscription abandoned = new RevenueCatSubscription("twinly_monthly", RevenueCatSubscription.APP_STORE, "normal",
                NOW.minus(Duration.ofDays(120)), NOW.minus(Duration.ofDays(90)), null, NOW.minus(Duration.ofDays(90)), null);

        // when: 판정
        RewardDecision decision = InviteRewardJudge.judge(new RevenueCatSubscriptions(null, List.of(abandoned)), NOW);

        // then: 무료 지급
        assertThat(decision.kind()).isEqualTo(RewardDecision.Kind.PROMOTIONAL);
    }

    @Test
    @DisplayName("해지 예약한 유료 구독은 그 만료 시각부터 30일 무료 지급이다")
    void judge_cancelled_paid_is_promotional_after_expiry() {
        // given: 6일 남은 해지 예약 구독
        Instant expiresAt = NOW.plus(Duration.ofDays(6));
        RevenueCatSubscription cancelled = new RevenueCatSubscription("twinly_monthly", RevenueCatSubscription.APP_STORE, "normal",
                NOW.minus(Duration.ofDays(24)), expiresAt, NOW.minus(Duration.ofDays(1)), null, null);

        // when: 판정
        RewardDecision decision = InviteRewardJudge.judge(new RevenueCatSubscriptions(expiresAt, List.of(cancelled)), NOW);

        // then: 만료 뒤 30일
        assertThat(decision.kind()).isEqualTo(RewardDecision.Kind.PROMOTIONAL);
        assertThat(decision.targetExpiresAt()).isEqualTo(expiresAt.plus(Duration.ofDays(30)));
    }
}
