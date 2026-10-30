package com.nidus.twinly.season.service;

import com.nidus.twinly.season.domain.SeasonRecapData;
import com.nidus.twinly.season.domain.SeasonRecapPeriod;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.generator.SeasonRecapGenerator;
import com.nidus.twinly.season.repository.SeasonRepository;
import com.nidus.twinly.season.writer.SeasonRecapWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SeasonRecapServiceUnitTest {

    private static final Instant RESET_AT = Instant.parse("2026-10-31T15:00:03Z");

    @Mock
    SeasonRepository seasonRepository;

    @Mock
    SeasonRecapGenerator seasonRecapGenerator;

    @Mock
    SeasonRecapWriter seasonRecapWriter;

    @InjectMocks
    SeasonRecapService seasonRecapService;

    @Test
    @DisplayName("지난 시즌이 없으면 아무것도 하지 않는다")
    void prepare_without_previous_season_does_nothing() {
        // given: 첫 시즌만 활성
        Season current = season(1L, Instant.parse("2026-09-30T15:00:00Z"), Instant.parse("2026-10-31T14:59:59Z"), RESET_AT, null);
        given(seasonRepository.findFirstByIsActiveTrueOrderByIdDesc()).willReturn(Optional.of(current));
        given(seasonRepository.findFirstByIdLessThanOrderByIdDesc(1L)).willReturn(Optional.empty());

        // when: 준비 실행
        seasonRecapService.prepare(Instant.now());

        // then: 지우기·생성·알림 모두 없음
        then(seasonRecapWriter).should(never()).deleteExpired(anyLong());
        then(seasonRecapGenerator).should(never()).generate(anyLong(), any());
    }

    @Test
    @DisplayName("리셋 직후에는 끝난 리캡을 지우고 지난 시즌 리캡을 집계 기간으로 만들지만, 오전 10시 전이면 알리지 않는다")
    void prepare_creates_but_waits_until_ten() {
        // given: 7번 시즌(리캡 없음) → 8번 시즌이 KST 11-01 00:00:03에 생성됨, 지금은 KST 00:05
        Season previous = season(7L, Instant.parse("2026-09-30T15:00:00Z"), Instant.parse("2026-10-31T14:59:59Z"), Instant.parse("2026-09-30T15:00:01Z"), null);
        Season current = season(8L, RESET_AT, Instant.parse("2026-11-30T14:59:59Z"), RESET_AT, null);
        givenSeasons(previous, current);
        Map<Long, SeasonRecapData> generated = Map.of(1L, mock(SeasonRecapData.class));
        given(seasonRecapGenerator.generate(7L, new SeasonRecapPeriod(previous.getStartedAt(), previous.getEndedAt()))).willReturn(generated);

        // when: 준비 실행
        seasonRecapService.prepare(Instant.parse("2026-10-31T15:05:00Z"));

        // then: 6번 이전 리캡 삭제 → 생성 저장, 알림은 아직
        then(seasonRecapWriter).should().deleteExpired(7L);
        then(seasonRecapWriter).should().create(7L, generated);
        then(seasonRecapWriter).should(never()).notifyAll(anyLong());
    }

    @Test
    @DisplayName("이미 만든 리캡은 다시 집계하지 않고, 리셋 당일 오전 10시가 지나면 알림을 맡긴다")
    void prepare_notifies_after_ten_without_regenerating() {
        // given: 리캡이 이미 만들어진 7번 시즌, 지금은 KST 11-01 10:00
        Season previous = season(7L, Instant.parse("2026-09-30T15:00:00Z"), Instant.parse("2026-10-31T14:59:59Z"), Instant.parse("2026-09-30T15:00:01Z"), Instant.parse("2026-10-31T15:05:10Z"));
        Season current = season(8L, RESET_AT, Instant.parse("2026-11-30T14:59:59Z"), RESET_AT, null);
        givenSeasons(previous, current);

        // when: 준비 실행
        seasonRecapService.prepare(Instant.parse("2026-11-01T01:00:00Z"));

        // then: 다시 집계하지 않고 알림만
        then(seasonRecapGenerator).should(never()).generate(anyLong(), any());
        then(seasonRecapWriter).should().notifyAll(7L);
    }

    private void givenSeasons(Season previous, Season current) {
        given(seasonRepository.findFirstByIsActiveTrueOrderByIdDesc()).willReturn(Optional.of(current));
        given(seasonRepository.findFirstByIdLessThanOrderByIdDesc(current.getId())).willReturn(Optional.of(previous));
    }

    private Season season(Long id, Instant startedAt, Instant endedAt, Instant createdAt, Instant recapCreatedAt) {
        Season season = BeanUtils.instantiateClass(Season.class);
        ReflectionTestUtils.setField(season, "id", id);
        ReflectionTestUtils.setField(season, "startedAt", startedAt);
        ReflectionTestUtils.setField(season, "endedAt", endedAt);
        ReflectionTestUtils.setField(season, "activatedAt", createdAt);
        ReflectionTestUtils.setField(season, "recapCreatedAt", recapCreatedAt);
        return season;
    }
}
