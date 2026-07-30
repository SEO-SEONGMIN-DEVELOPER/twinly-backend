package com.nidus.twinly.season.service;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.season.dto.result.SeasonParticipationResult;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.entity.SeasonParticipation;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SeasonServiceUnitTest {

    private static final Long CURRENT_SEASON_ID = 1L;
    private static final Long USER_ID = 10L;

    @Mock
    CurrentSeasonReader currentSeasonReader;

    @Mock
    SeasonParticipationRepository seasonParticipationRepository;

    @InjectMocks
    SeasonService seasonService;

    @Test
    @DisplayName("활성 시즌이 없으면 IllegalStateException이 발생하고 참가를 저장하지 않는다")
    void participateIn_when_current_season_missing_throws() {
        // given: 현재 시즌 조회가 실패하는 상황 (예외 발생 자체는 CurrentSeasonReaderUnitTest에서 검증)
        given(currentSeasonReader.read()).willThrow(new IllegalStateException("활성화된 시즌이 존재하지 않습니다."));

        // when & then: IllegalStateException 발생 + 참가 저장 안 함
        assertThatThrownBy(() -> seasonService.participateIn(USER_ID))
                .isInstanceOf(IllegalStateException.class);

        then(seasonParticipationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("시즌 시작 전이면 SEASON_NOT_JOINABLE 예외가 발생하고 참가를 저장하지 않는다")
    void participateIn_before_season_start_throws() {
        // given: 아직 시작하지 않은 시즌
        Instant now = Instant.now();
        given(currentSeasonReader.read())
                .willReturn(season(now.plus(Duration.ofDays(1)), now.plus(Duration.ofDays(2))));

        // when & then: SEASON_NOT_JOINABLE 예외 발생 + 참가 저장 안 함
        assertThatThrownBy(() -> seasonService.participateIn(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SEASON_NOT_JOINABLE);

        then(seasonParticipationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("시즌 종료 후면 SEASON_NOT_JOINABLE 예외가 발생하고 참가를 저장하지 않는다")
    void participateIn_after_season_end_throws() {
        // given: 이미 종료된 시즌
        Instant now = Instant.now();
        given(currentSeasonReader.read())
                .willReturn(season(now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(1))));

        // when & then: SEASON_NOT_JOINABLE 예외 발생 + 참가 저장 안 함
        assertThatThrownBy(() -> seasonService.participateIn(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SEASON_NOT_JOINABLE);

        then(seasonParticipationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("시즌 참가는 조회 없이 upsert 한 번으로 위임한다")
    void participateIn_delegates_to_upsert() {
        // given: 참가 가능한 기간
        given(currentSeasonReader.read()).willReturn(joinableSeason());

        // when: 참가
        seasonService.participateIn(USER_ID);

        // then: 조회-후-저장이 아니라 원자적 upsert 한 번 (참가 버튼 연타가 유니크 제약을 위반하지 않는다)
        then(seasonParticipationRepository).should().upsert(USER_ID, CURRENT_SEASON_ID);
        then(seasonParticipationRepository).should(never()).existsByUserIdAndSeasonId(any(), any());
        then(seasonParticipationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("참가 이력이 있으면 현재 시즌 id와 참가 시각을 반환한다")
    void participation_when_participated_returns_participatedInAt() {
        // given: 현재 시즌에 참가 이력이 있고 참가 시각이 기록됨
        given(currentSeasonReader.read()).willReturn(joinableSeason());
        Instant participatedInAt = Instant.parse("2026-07-01T00:00:00Z");
        SeasonParticipation participation = SeasonParticipation.create(USER_ID, CURRENT_SEASON_ID);
        ReflectionTestUtils.setField(participation, "participatedInAt", participatedInAt);
        given(seasonParticipationRepository.findByUserIdAndSeasonId(USER_ID, CURRENT_SEASON_ID))
                .willReturn(Optional.of(participation));

        // when: 참가 정보 조회
        SeasonParticipationResult result = seasonService.participation(USER_ID);

        // then: 현재 시즌 id와 기록된 참가 시각이 담김
        assertThat(result.currentSeasonId()).isEqualTo(CURRENT_SEASON_ID);
        assertThat(result.participatedInAt()).isEqualTo(participatedInAt);
    }

    @Test
    @DisplayName("참가 이력이 없으면 현재 시즌 id만 반환하고 참가 시각은 null이다")
    void participation_when_not_participated_returns_null_participatedInAt() {
        // given: 현재 시즌에 참가 이력이 없음
        given(currentSeasonReader.read()).willReturn(joinableSeason());
        given(seasonParticipationRepository.findByUserIdAndSeasonId(USER_ID, CURRENT_SEASON_ID))
                .willReturn(Optional.empty());

        // when: 참가 정보 조회
        SeasonParticipationResult result = seasonService.participation(USER_ID);

        // then: 현재 시즌 id는 채워지고 참가 시각은 null
        assertThat(result.currentSeasonId()).isEqualTo(CURRENT_SEASON_ID);
        assertThat(result.participatedInAt()).isNull();
    }

    /** 지금이 참가 기간에 포함되는 시즌. */
    private Season joinableSeason() {
        Instant now = Instant.now();
        return season(now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(1)));
    }

    /** Season은 생성 팩토리·세터가 없으므로 protected 기본 생성자 + 리플렉션으로 만든다. */
    private Season season(Instant startedAt, Instant endedAt) {
        Season season = BeanUtils.instantiateClass(Season.class);
        ReflectionTestUtils.setField(season, "id", CURRENT_SEASON_ID);
        ReflectionTestUtils.setField(season, "startedAt", startedAt);
        ReflectionTestUtils.setField(season, "endedAt", endedAt);
        return season;
    }
}
