package com.nidus.twinly.world.domain;

import com.nidus.twinly.common.web.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrackWindowUnitTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 16);
    private static final TrackWindow WINDOW = TrackWindow.of(DATE, "2026-10-16T13:00:00+09:00", "2026-10-16T14:00:00+09:00");

    @Test
    @DisplayName("오프셋이 달라도 같은 순간이면 한국 시각으로 바꾸고, 공백으로 풀린 + 도 읽는다")
    void of_converts_to_kst() {
        // when & then: Z 오프셋의 같은 순간과 + 가 공백으로 풀린 값이 +09:00 창과 같다
        assertThat(TrackWindow.of(DATE, "2026-10-16T04:00:00Z", "2026-10-16T05:00:00Z")).isEqualTo(WINDOW);
        assertThat(TrackWindow.of(DATE, "2026-10-16T13:00:00 09:00", "2026-10-16T14:00:00 09:00")).isEqualTo(WINDOW);
    }

    @Test
    @DisplayName("오프셋 없음·정각 아님·1시간 아님·회차 밖은 거절한다 (회차 끝 03~04시 창은 받음)")
    void of_rejects_invalid_windows() {
        // when & then: 오프셋 없음·정각 아님·2시간·회차 시작 전·회차 끝 뒤는 예외
        for (String[] window : new String[][]{
                {"2026-10-16T13:00:00", "2026-10-16T14:00:00"},
                {"2026-10-16T13:30:00+09:00", "2026-10-16T14:30:00+09:00"},
                {"2026-10-16T13:00:00+09:00", "2026-10-16T15:00:00+09:00"},
                {"2026-10-16T05:00:00+09:00", "2026-10-16T06:00:00+09:00"},
                {"2026-10-17T04:00:00+09:00", "2026-10-17T05:00:00+09:00"}}) {
            assertThatThrownBy(() -> TrackWindow.of(DATE, window[0], window[1])).isInstanceOf(BusinessException.class);
        }

        // when & then: 회차 마지막 창(다음 날 03~04시)은 받는다
        assertThat(TrackWindow.of(DATE, "2026-10-17T03:00:00+09:00", "2026-10-17T04:00:00+09:00").to())
                .isEqualTo(LocalDateTime.of(2026, 10, 17, 4, 0));
    }

    @Test
    @DisplayName("구간은 [from, to) 와 겹치면 통째로, 길이 0 은 from ≤ start < to 일 때만 들어간다")
    void overlaps_follows_window_rules() {
        // when & then: 걸친 구간은 들어가고, 창 끝에서 시작하거나 창 시작에서 끝나는 구간은 빠지며, 길이 0 은 [from, to) 안일 때만
        assertThat(WINDOW.overlaps(at(12, 50), at(13, 10))).isTrue();
        assertThat(WINDOW.overlaps(at(13, 59), at(14, 30))).isTrue();
        assertThat(WINDOW.overlaps(at(12, 0), at(13, 0))).isFalse();
        assertThat(WINDOW.overlaps(at(14, 0), at(14, 30))).isFalse();
        assertThat(WINDOW.overlaps(at(13, 0), at(13, 0))).isTrue();
        assertThat(WINDOW.overlaps(at(14, 0), at(14, 0))).isFalse();
    }

    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2026, 10, 16, hour, minute);
    }
}
