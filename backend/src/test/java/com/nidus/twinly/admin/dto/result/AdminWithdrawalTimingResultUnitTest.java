package com.nidus.twinly.admin.dto.result;

import com.nidus.twinly.admin.domain.WithdrawalStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AdminWithdrawalTimingResultUnitTest {

    @Test
    @DisplayName("시즌 참가 기록이 없으면 구독 전 탈퇴이고 가입일부터 KST 날짜 수를 센다")
    void beforeSubscription_countsKstDaysFromJoin() {
        // given: KST 10-01 23:30 가입, KST 10-02 00:10 탈퇴 (40분 차이지만 KST 날짜로는 하루)
        Instant joinedAt = Instant.parse("2026-10-01T14:30:00Z");
        Instant withdrawnAt = Instant.parse("2026-10-01T15:10:00Z");

        // when
        AdminWithdrawalTimingResult timing = AdminWithdrawalTimingResult.of(joinedAt, null, withdrawnAt);

        // then
        assertThat(timing.stage()).isEqualTo(WithdrawalStage.BEFORE_SUBSCRIPTION);
        assertThat(timing.days()).isEqualTo(1);
    }

    @Test
    @DisplayName("탈퇴 전에 시즌에 참가했으면 구독 후 탈퇴이고 첫 참가일부터 센다")
    void afterSubscription_countsFromFirstParticipation() {
        // given: 10-01 가입, 10-03 첫 참가, 10-10 탈퇴 (모두 KST 낮 시간)
        Instant joinedAt = Instant.parse("2026-10-01T03:00:00Z");
        Instant subscribedAt = Instant.parse("2026-10-03T03:00:00Z");
        Instant withdrawnAt = Instant.parse("2026-10-10T03:00:00Z");

        // when
        AdminWithdrawalTimingResult timing = AdminWithdrawalTimingResult.of(joinedAt, subscribedAt, withdrawnAt);

        // then
        assertThat(timing.stage()).isEqualTo(WithdrawalStage.AFTER_SUBSCRIPTION);
        assertThat(timing.days()).isEqualTo(7);
    }

    @Test
    @DisplayName("참가 기록이 탈퇴 사유 접수보다 뒤면 구독 전 탈퇴로 본다")
    void participationAfterWithdrawal_isBeforeSubscription() {
        // given: 탈퇴 사유를 낸 뒤에 생긴 참가 기록
        Instant joinedAt = Instant.parse("2026-10-01T03:00:00Z");
        Instant withdrawnAt = Instant.parse("2026-10-05T03:00:00Z");
        Instant subscribedAt = Instant.parse("2026-10-06T03:00:00Z");

        // when
        AdminWithdrawalTimingResult timing = AdminWithdrawalTimingResult.of(joinedAt, subscribedAt, withdrawnAt);

        // then
        assertThat(timing.stage()).isEqualTo(WithdrawalStage.BEFORE_SUBSCRIPTION);
        assertThat(timing.days()).isEqualTo(4);
    }
}
