package com.nidus.twinly.season.reader;

import com.nidus.twinly.season.domain.SeasonScope;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.repository.SeasonRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CurrentSeasonReaderUnitTest {

    private static final Long CURRENT_SEASON_ID = 7L;

    @Mock
    SeasonRepository seasonRepository;

    @InjectMocks
    CurrentSeasonReader currentSeasonReader;

    @Test
    @DisplayName("활성 시즌이 있으면 그 시즌을 현재 시즌으로 반환한다")
    void read_returns_active_season() {
        // given: 활성 시즌 중 가장 최신 시즌이 조회됨
        given(seasonRepository.findFirstByIsActiveTrueOrderByIdDesc()).willReturn(Optional.of(season()));

        // when: 현재 시즌 조회
        Season result = currentSeasonReader.read();

        // then: 조회된 시즌이 그대로 반환됨
        assertThat(result.getId()).isEqualTo(CURRENT_SEASON_ID);
    }

    @Test
    @DisplayName("활성 시즌이 하나도 없으면 IllegalStateException이 발생한다")
    void read_when_no_active_season_throws() {
        // given: 활성 시즌이 하나도 없음
        given(seasonRepository.findFirstByIsActiveTrueOrderByIdDesc()).willReturn(Optional.empty());

        // when & then: 유저 입력 문제가 아닌 운영 데이터 문제이므로 IllegalStateException으로 터뜨린다
        assertThatThrownBy(() -> currentSeasonReader.read())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("활성화된 시즌");
    }

    /** Season은 생성 팩토리·세터가 없으므로 protected 기본 생성자 + 리플렉션으로 만든다. */
    @Test
    @DisplayName("현재 시즌 안의 회차는 현재 시즌 id 와 시작일을 돌려준다")
    void scopeOf_within_current_season() {
        givenActiveSeason("2026-09-30T15:00:00Z", "2026-10-31T14:59:59Z");

        assertThat(currentSeasonReader.scopeOf(LocalDate.of(2026, 10, 31))).isEqualTo(new SeasonScope(CURRENT_SEASON_ID, LocalDate.of(2026, 10, 1)));
    }

    @Test
    @DisplayName("종료 시각이 23:59:59 면 다음 날부터, 자정이면 그 날부터 다음 시즌 회차로 본다")
    void scopeOf_next_season_boundary() {
        givenActiveSeason("2026-09-30T15:00:00Z", "2026-10-31T14:59:59Z");
        assertThat(currentSeasonReader.startDateOf(LocalDate.of(2026, 11, 1))).isEqualTo(LocalDate.of(2026, 11, 1));

        givenActiveSeason("2026-09-30T15:00:00Z", "2026-10-31T15:00:00Z");
        assertThat(currentSeasonReader.startDateOf(LocalDate.of(2026, 10, 31))).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(currentSeasonReader.startDateOf(LocalDate.of(2026, 11, 1))).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    @DisplayName("다음 시즌 회차는 예약된 시즌 id 를 돌려주고, 예약이 없으면 id 없이 시작일만 돌려준다")
    void scopeOf_next_season_uses_reserved_id() {
        givenActiveSeason("2026-09-30T15:00:00Z", "2026-10-31T15:00:00Z");

        assertThat(currentSeasonReader.scopeOf(LocalDate.of(2026, 11, 1))).isEqualTo(new SeasonScope(null, LocalDate.of(2026, 11, 1)));

        Season reserved = season();
        ReflectionTestUtils.setField(reserved, "id", 9L);
        given(seasonRepository.findFirstByIsActiveFalseAndActivatedAtIsNullOrderByStartedAtAsc()).willReturn(Optional.of(reserved));

        assertThat(currentSeasonReader.scopeOf(LocalDate.of(2026, 11, 1))).isEqualTo(new SeasonScope(9L, LocalDate.of(2026, 11, 1)));
    }

    @Test
    @DisplayName("현재 시즌 이전 회차는 그 회차가 속한 이전 시즌을 돌려준다")
    void scopeOf_before_current_season() {
        givenActiveSeason("2026-10-31T15:00:00Z", "2026-11-30T15:00:00Z");
        Season previous = season();
        ReflectionTestUtils.setField(previous, "id", 3L);
        ReflectionTestUtils.setField(previous, "startedAt", Instant.parse("2026-09-30T15:00:00Z"));
        given(seasonRepository.findFirstByStartedAtBeforeOrderByStartedAtDesc(Instant.parse("2026-10-31T15:00:00Z")))
                .willReturn(Optional.of(previous));

        assertThat(currentSeasonReader.scopeOf(LocalDate.of(2026, 10, 31))).isEqualTo(new SeasonScope(3L, LocalDate.of(2026, 10, 1)));
    }

    @Test
    @DisplayName("활성 시즌이 없으면 시즌으로 자르지 않는다")
    void scopeOf_without_active_season() {
        given(seasonRepository.findFirstByIsActiveTrueOrderByIdDesc()).willReturn(Optional.empty());

        assertThat(currentSeasonReader.scopeOf(LocalDate.of(2026, 11, 1))).isEqualTo(SeasonScope.UNBOUNDED);
    }

    private void givenActiveSeason(String startedAt, String endedAt) {
        Season season = season();
        ReflectionTestUtils.setField(season, "startedAt", Instant.parse(startedAt));
        ReflectionTestUtils.setField(season, "endedAt", Instant.parse(endedAt));
        given(seasonRepository.findFirstByIsActiveTrueOrderByIdDesc()).willReturn(Optional.of(season));
    }

    private Season season() {
        Season season = BeanUtils.instantiateClass(Season.class);
        ReflectionTestUtils.setField(season, "id", CURRENT_SEASON_ID);
        return season;
    }
}
