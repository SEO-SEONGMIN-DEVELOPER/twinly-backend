package com.nidus.twinly.season.writer;

import com.nidus.twinly.notification.writer.AppNotificationFeedWriter;
import com.nidus.twinly.season.domain.SeasonRecapData;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.entity.SeasonRecap;
import com.nidus.twinly.season.repository.SeasonRecapRepository;
import com.nidus.twinly.season.repository.SeasonRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SeasonRecapWriterUnitTest {

    private static final Long SEASON_ID = 7L;

    @Mock
    SeasonRepository seasonRepository;

    @Mock
    SeasonRecapRepository seasonRecapRepository;

    @Mock
    AppNotificationFeedWriter appNotificationFeedWriter;

    @Mock
    ObjectMapper objectMapper;

    @InjectMocks
    SeasonRecapWriter seasonRecapWriter;

    @Test
    @DisplayName("리캡을 처음 만들면 전원에게 같은 준비 시각으로 저장하고 시즌에 준비 시각을 남긴다")
    void create_saves_all_with_same_created_at() {
        // given: 아직 리캡을 만들지 않은 시즌 + 2명분 데이터
        Season season = season(null, null);
        given(seasonRepository.findById(SEASON_ID)).willReturn(Optional.of(season));
        SeasonRecapData first = mock(SeasonRecapData.class);
        SeasonRecapData second = mock(SeasonRecapData.class);
        given(objectMapper.writeValueAsString(first)).willReturn("{\"a\":1}");
        given(objectMapper.writeValueAsString(second)).willReturn("{\"a\":2}");

        // when: 리캡 저장
        seasonRecapWriter.create(SEASON_ID, Map.of(1L, first, 2L, second));

        // then: 2건이 같은 createdAt으로 저장되고 시즌에도 같은 시각이 기록됨
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SeasonRecap>> captor = ArgumentCaptor.forClass(List.class);
        then(seasonRecapRepository).should().saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(SeasonRecap::getUserId).containsExactlyInAnyOrder(1L, 2L);
        assertThat(captor.getValue()).extracting(SeasonRecap::getSeasonId).containsOnly(SEASON_ID);
        assertThat(captor.getValue()).extracting(SeasonRecap::getCreatedAt).containsOnly(season.getRecapCreatedAt());
        assertThat(season.getRecapCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 리캡을 만든 시즌이면 다시 저장하지 않는다 (숫자·준비 시각 유지)")
    void create_is_idempotent() {
        // given: 이미 리캡을 만든 시즌
        Instant createdAt = Instant.parse("2026-10-31T15:03:12Z");
        given(seasonRepository.findById(SEASON_ID)).willReturn(Optional.of(season(createdAt, null)));

        // when: 다시 저장 시도
        seasonRecapWriter.create(SEASON_ID, Map.of(1L, mock(SeasonRecapData.class)));

        // then: 저장하지 않음
        then(seasonRecapRepository).should(never()).saveAll(anyList());
    }

    @Test
    @DisplayName("리캡 알림은 시즌 시작 월로 탈퇴하지 않은 받는 사람에게 한 번만 만들고 발송 시각을 남긴다")
    void notifyAll_writes_feeds_once() {
        // given: 리캡은 있고 아직 안 보낸 시즌 (KST 10월 1일 시작)
        Season season = season(Instant.parse("2026-10-31T15:03:12Z"), null);
        given(seasonRepository.findById(SEASON_ID)).willReturn(Optional.of(season));
        given(seasonRecapRepository.findAllNotifiableUserIdsBySeasonId(SEASON_ID)).willReturn(List.of(1L, 2L));

        // when: 알림 생성
        seasonRecapWriter.notifyAll(SEASON_ID);

        // then: 10월 리캡 피드를 만들고 발송 시각 기록
        then(appNotificationFeedWriter).should().writeSeasonRecap(List.of(1L, 2L), SEASON_ID, 10);
        assertThat(season.getRecapNotifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 알렸거나 리캡이 준비되지 않은 시즌이면 알림을 만들지 않는다")
    void notifyAll_skips_when_already_notified_or_not_created() {
        // given: 이미 알린 시즌
        given(seasonRepository.findById(SEASON_ID)).willReturn(Optional.of(
                season(Instant.parse("2026-10-31T15:03:12Z"), Instant.parse("2026-11-01T01:00:00Z"))));

        // when: 알림 생성 시도
        seasonRecapWriter.notifyAll(SEASON_ID);

        // then: 피드를 만들지 않음
        then(appNotificationFeedWriter).should(never()).writeSeasonRecap(anyList(), anyLong(), anyInt());
        then(seasonRecapRepository).should(never()).findAllNotifiableUserIdsBySeasonId(any());
    }

    private Season season(Instant recapCreatedAt, Instant recapNotifiedAt) {
        Season season = BeanUtils.instantiateClass(Season.class);
        ReflectionTestUtils.setField(season, "id", SEASON_ID);
        ReflectionTestUtils.setField(season, "startedAt", Instant.parse("2026-09-30T15:00:00Z"));
        ReflectionTestUtils.setField(season, "endedAt", Instant.parse("2026-10-31T14:59:59Z"));
        ReflectionTestUtils.setField(season, "recapCreatedAt", recapCreatedAt);
        ReflectionTestUtils.setField(season, "recapNotifiedAt", recapNotifiedAt);
        return season;
    }
}
