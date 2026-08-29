package com.nidus.twinly.season.service;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.season.dto.command.SeasonChangeCommand;
import com.nidus.twinly.season.dto.result.SeasonChangeResult;
import com.nidus.twinly.season.dto.result.SeasonParticipationResult;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.entity.SeasonParticipation;
import com.nidus.twinly.season.event.SeasonChangedEvent;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import com.nidus.twinly.purchase.service.PurchaseService;
import com.nidus.twinly.season.repository.SeasonRepository;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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

    @Mock
    SeasonRepository seasonRepository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Mock
    PurchaseService purchaseService;

    @Mock
    UserRepository userRepository;

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
    @DisplayName("참가 조회 시 구매 상태 동기화를 위임한다")
    void participation_delegates_purchase_sync() {
        // given: 현재 시즌이 있고 유저도 존재
        given(currentSeasonReader.read()).willReturn(joinableSeason());
        User user = user();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        // when: 참가 정보 조회 (앱 시작 시 가장 먼저 호출되는 API)
        seasonService.participation(USER_ID);

        // then: 조건부 동기화를 위임 (웹훅 유실이 앱 시작 시점에 복구된다)
        then(purchaseService).should().syncIfStale(user);
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

    @Test
    @DisplayName("시즌 전환 시 기존 활성 시즌을 모두 비활성화하고 새 시즌을 활성 상태로 저장한다")
    void changeSeason_deactivatesPreviousAndSavesNew() {
        // given: 활성 시즌 두 개가 남아 있는 상태 (이상 데이터까지 함께 정리되어야 한다)
        Season previous = joinableSeason();
        Season strayActive = joinableSeason();
        given(seasonRepository.findAllByIsActiveTrue()).willReturn(List.of(previous, strayActive));
        given(seasonRepository.save(any(Season.class))).willAnswer(invocation -> invocation.getArgument(0));

        Instant startedAt = Instant.parse("2026-09-01T00:00:00Z");
        Instant endedAt = Instant.parse("2026-12-01T00:00:00Z");

        // when
        SeasonChangeResult result = seasonService.changeSeason(new SeasonChangeCommand(startedAt, endedAt));

        // then: 기존 활성 시즌은 전부 꺼지고, 새 시즌만 활성이다
        assertThat(previous.getIsActive()).isFalse();
        assertThat(strayActive.getIsActive()).isFalse();

        ArgumentCaptor<Season> saved = ArgumentCaptor.forClass(Season.class);
        then(seasonRepository).should().save(saved.capture());
        assertThat(saved.getValue().getIsActive()).isTrue();
        assertThat(saved.getValue().getStartedAt()).isEqualTo(startedAt);
        assertThat(saved.getValue().getEndedAt()).isEqualTo(endedAt);

        assertThat(result.startedAt()).isEqualTo(startedAt);
        assertThat(result.endedAt()).isEqualTo(endedAt);
    }

    @Test
    @DisplayName("시즌 전환이 커밋될 수 있도록 SeasonChangedEvent를 발행한다")
    void changeSeason_publishesSeasonChangedEvent() {
        // given
        given(seasonRepository.findAllByIsActiveTrue()).willReturn(List.of());
        given(seasonRepository.save(any(Season.class))).willAnswer(invocation -> {
            Season season = invocation.getArgument(0);
            ReflectionTestUtils.setField(season, "id", 77L);
            return season;
        });

        // when
        seasonService.changeSeason(new SeasonChangeCommand(
                Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-12-01T00:00:00Z")));

        // then: 새 시즌 id를 담은 이벤트가 나가야 소켓 알림이 이어진다
        then(eventPublisher).should().publishEvent(new SeasonChangedEvent(77L));
    }

    @Test
    @DisplayName("시작 시각이 종료 시각보다 앞서지 않으면 INVALID_SEASON_PERIOD 예외가 발생하고 아무것도 저장하지 않는다")
    void changeSeason_rejectsInvalidPeriod() {
        // given: 시작과 종료가 같은 구간
        Instant sameInstant = Instant.parse("2026-09-01T00:00:00Z");

        // when & then
        assertThatThrownBy(() -> seasonService.changeSeason(new SeasonChangeCommand(sameInstant, sameInstant)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_SEASON_PERIOD);

        then(seasonRepository).should(never()).save(any());
        then(eventPublisher).should(never()).publishEvent(any(SeasonChangedEvent.class));
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

    private User user() {
        User user = User.create(
                "nick", "홍", "familyHash", "길동", "givenHash",
                Gender.MALE, "organization", "organizationHash", "니두스", "affHash", "2020123", "affNoHash",
                "2000-01-01", "birthHash", "01000000000", "phoneHash", "me@test.com", "emailHash");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }
}
