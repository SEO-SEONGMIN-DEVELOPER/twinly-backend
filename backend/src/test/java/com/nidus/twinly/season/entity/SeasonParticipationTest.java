package com.nidus.twinly.season.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeasonParticipationTest {

    @Test
    @DisplayName("참가 시각과 생성 시각은 같은 이벤트의 시각이므로 정확히 일치한다")
    void participated_in_at_and_created_at_are_same_instant() {
        // when: 시즌 참가 생성
        SeasonParticipation participation = SeasonParticipation.create(1L, 2L);

        // then: Instant.now()를 두 번 부르면 두 값이 미세하게 어긋나 로그·통계 대조가 어긋난다
        assertThat(participation.getParticipatedInAt()).isEqualTo(participation.getCreatedAt());
    }
}
