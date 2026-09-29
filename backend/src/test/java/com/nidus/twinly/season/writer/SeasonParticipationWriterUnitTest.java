package com.nidus.twinly.season.writer;

import com.nidus.twinly.legal.domain.PolicyKind;
import com.nidus.twinly.legal.reader.ConsentReader;
import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.event.SeasonParticipationStartedEvent;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SeasonParticipationWriterUnitTest {

    private static final Long CURRENT_SEASON_ID = 1L;
    private static final Long NEW_SEASON_ID = 2L;

    @Mock
    CurrentSeasonReader currentSeasonReader;

    @Mock
    EntitlementReader entitlementReader;

    @Mock
    ConsentReader consentReader;

    @Mock
    SeasonParticipationRepository seasonParticipationRepository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    SeasonParticipationWriter seasonParticipationWriter;

    @Test
    @DisplayName("구독 중이고 필수 약관에 동의했으면 현재 시즌 참가를 upsert 한 번으로 위임한다")
    void participateInCurrentSeasonIfEligible_delegates_to_upsert() {
        // given: 활성 시즌이 있고 구독·필수 약관 동의를 모두 갖춘 유저
        given(entitlementReader.hasSimulationAccess(10L)).willReturn(true);
        given(consentReader.hasAgreedAllRequired(10L, PolicyKind.PARALLEL_ENTRY)).willReturn(true);
        given(currentSeasonReader.read()).willReturn(season());

        // when: 현재 시즌 참가
        seasonParticipationWriter.participateInCurrentSeasonIfEligible(10L);

        // then: 동기화가 반복돼도 유니크 제약을 위반하지 않도록 원자적 upsert 한 번
        then(seasonParticipationRepository).should().upsert(10L, CURRENT_SEASON_ID);
        then(seasonParticipationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("구독 중이어도 필수 약관에 동의하지 않았으면 현재 시즌에 참가시키지 않는다")
    void participateInCurrentSeasonIfEligible_skips_without_consent() {
        // given: 구독은 있지만 평행우주 입장 필수 약관 최신 버전에 동의하지 않음
        given(entitlementReader.hasSimulationAccess(10L)).willReturn(true);
        given(consentReader.hasAgreedAllRequired(10L, PolicyKind.PARALLEL_ENTRY)).willReturn(false);

        // when: 현재 시즌 참가 시도
        seasonParticipationWriter.participateInCurrentSeasonIfEligible(10L);

        // then: 시뮬레이션 대상이 아니므로 앱에 참여 중으로 보이지 않도록 행을 만들지 않는다
        then(seasonParticipationRepository).should(never()).upsert(any(), any());
    }

    @Test
    @DisplayName("구독이 없으면 동의 여부를 보지 않고 현재 시즌에 참가시키지 않는다")
    void participateInCurrentSeasonIfEligible_skips_without_access() {
        // given: 약관 동의만 하고 구독은 없는 유저
        given(entitlementReader.hasSimulationAccess(10L)).willReturn(false);

        // when: 현재 시즌 참가 시도
        seasonParticipationWriter.participateInCurrentSeasonIfEligible(10L);

        // then: 참가 행을 만들지 않는다
        then(seasonParticipationRepository).should(never()).upsert(any(), any());
        then(consentReader).should(never()).hasAgreedAllRequired(any(), any());
    }

    @Test
    @DisplayName("현재 시즌에 처음 참가하면 시즌 참가 시작 이벤트를 발행한다")
    void participateInCurrentSeason_first_in_season_publishes_event() {
        // given: 활성 시즌에 아직 참가 행이 없는 유저 (약관 동의로 막 참가 조건을 갖춤)
        given(currentSeasonReader.read()).willReturn(season());
        given(seasonParticipationRepository.existsByUserIdAndSeasonId(10L, CURRENT_SEASON_ID)).willReturn(false);

        // when: 현재 시즌 참가
        seasonParticipationWriter.participateInCurrentSeason(10L);

        // then: 참가를 저장하고, 선생성이 이어지도록 이 유저의 참가 시작 이벤트가 참가 시각과 함께 나간다
        then(seasonParticipationRepository).should().upsert(10L, CURRENT_SEASON_ID);
        ArgumentCaptor<SeasonParticipationStartedEvent> captor = ArgumentCaptor.forClass(SeasonParticipationStartedEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(10L);
        assertThat(captor.getValue().participatedAt()).isNotNull();
    }

    @Test
    @DisplayName("현재 시즌에 이미 참가한 유저면 참가는 upsert 하되 이벤트를 다시 발행하지 않는다")
    void participateInCurrentSeason_already_in_season_does_not_publish_event() {
        // given: 같은 시즌 참가 행이 이미 있는 유저 (구매 동기화·참가 요청이 반복되는 경우)
        given(currentSeasonReader.read()).willReturn(season());
        given(seasonParticipationRepository.existsByUserIdAndSeasonId(10L, CURRENT_SEASON_ID)).willReturn(true);

        // when: 현재 시즌 참가
        seasonParticipationWriter.participateInCurrentSeason(10L);

        // then: 최초 참가 시각은 upsert 가 지키고, 선생성은 다시 요청하지 않는다
        then(seasonParticipationRepository).should().upsert(10L, CURRENT_SEASON_ID);
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("활성 시즌이 없으면 참가를 저장하지 않고 예외가 전파된다")
    void participateInCurrentSeason_when_current_season_missing_throws() {
        // given: 참가 조건은 갖췄지만 활성 시즌이 없는 비정상 상태
        given(entitlementReader.hasSimulationAccess(10L)).willReturn(true);
        given(consentReader.hasAgreedAllRequired(10L, PolicyKind.PARALLEL_ENTRY)).willReturn(true);
        given(currentSeasonReader.read()).willThrow(new IllegalStateException("활성화된 시즌이 존재하지 않습니다."));

        // when & then: 참가 행을 만들지 않는다 (구매 동기화 재시도로 복구된다)
        assertThatThrownBy(() -> seasonParticipationWriter.participateInCurrentSeasonIfEligible(10L))
                .isInstanceOf(IllegalStateException.class);

        then(seasonParticipationRepository).should(never()).upsert(any(), any());
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("결제 상태이면서 필수 약관에 동의한 유저만 지정한 시즌에 참가시킨다")
    void participateAllEligible_upserts_only_consented_entitled_users() {
        // given: simulation_access 가 살아 있는 유저 세 명 중 두 명만 필수 약관에 동의
        given(entitlementReader.userIdsWithSimulationAccess()).willReturn(List.of(10L, 20L, 30L));
        given(consentReader.filterAgreedAllRequired(List.of(10L, 20L, 30L), PolicyKind.PARALLEL_ENTRY)).willReturn(List.of(10L, 20L));

        // when: 새 시즌으로 일괄 참가
        seasonParticipationWriter.participateAllEligible(NEW_SEASON_ID);

        // then: 동의한 두 명만 새 시즌 참가 행이 생기고, 이미 시뮬레이션을 받던 유저들이라 선생성 이벤트는 내지 않는다
        then(seasonParticipationRepository).should().upsert(10L, NEW_SEASON_ID);
        then(seasonParticipationRepository).should().upsert(20L, NEW_SEASON_ID);
        then(seasonParticipationRepository).should(never()).upsert(30L, NEW_SEASON_ID);
        then(eventPublisher).should(never()).publishEvent(any());
    }

    private Season season() {
        Season season = BeanUtils.instantiateClass(Season.class);
        ReflectionTestUtils.setField(season, "id", CURRENT_SEASON_ID);
        return season;
    }
}
