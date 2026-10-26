package com.nidus.twinly.season.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SeasonRecapDataTest {

    @Test
    @DisplayName("시간 구간은 시작 포함·끝 미포함으로 나뉜다")
    void timeSlot_boundaries() {
        // when & then: 6·11·17·22시가 각 구간의 시작이고 5시는 심야
        assertThat(SeasonRecapTimeSlot.of(5)).isEqualTo(SeasonRecapTimeSlot.LATE_NIGHT);
        assertThat(SeasonRecapTimeSlot.of(6)).isEqualTo(SeasonRecapTimeSlot.MORNING);
        assertThat(SeasonRecapTimeSlot.of(11)).isEqualTo(SeasonRecapTimeSlot.LUNCH);
        assertThat(SeasonRecapTimeSlot.of(17)).isEqualTo(SeasonRecapTimeSlot.EVENING);
        assertThat(SeasonRecapTimeSlot.of(22)).isEqualTo(SeasonRecapTimeSlot.LATE_NIGHT);
    }

    @Test
    @DisplayName("활동 유형은 가장 많은 구간이고, 같으면 오전 → 점심 → 저녁 → 심야 순으로 앞의 것이다")
    void activityType_picks_max_with_earlier_slot_on_tie() {
        // given: 점심과 심야가 5개로 같음
        Map<SeasonRecapTimeSlot, Integer> counts = Map.of(
                SeasonRecapTimeSlot.MORNING, 1,
                SeasonRecapTimeSlot.LUNCH, 5,
                SeasonRecapTimeSlot.LATE_NIGHT, 5);

        // when: 활동 유형 계산
        SeasonRecapData.ActivityType activityType = SeasonRecapData.ActivityType.of(counts);

        // then: 점심이 고르고 구간별 수가 그대로 담김
        assertThat(activityType.type()).isEqualTo(SeasonRecapTimeSlot.LUNCH);
        assertThat(activityType.morning()).isEqualTo(1);
        assertThat(activityType.evening()).isZero();
        assertThat(activityType.lateNight()).isEqualTo(5);
    }

    @Test
    @DisplayName("행동 합계가 10개 미만이면 활동 유형은 null이다")
    void activityType_under_ten_is_null() {
        // when & then: 합계 9개
        assertThat(SeasonRecapData.ActivityType.of(Map.of(SeasonRecapTimeSlot.EVENING, 9))).isNull();
    }
}
