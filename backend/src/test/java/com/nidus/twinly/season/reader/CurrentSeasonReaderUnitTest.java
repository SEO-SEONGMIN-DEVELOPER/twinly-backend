package com.nidus.twinly.season.reader;

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
    private Season season() {
        Season season = BeanUtils.instantiateClass(Season.class);
        ReflectionTestUtils.setField(season, "id", CURRENT_SEASON_ID);
        return season;
    }
}
