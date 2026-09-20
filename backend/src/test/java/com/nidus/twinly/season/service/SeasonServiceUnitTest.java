package com.nidus.twinly.season.service;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.legal.domain.PolicyKind;
import com.nidus.twinly.legal.reader.ConsentReader;
import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.season.dto.command.SeasonChangeCommand;
import com.nidus.twinly.season.dto.result.SeasonChangeResult;
import com.nidus.twinly.season.dto.result.SeasonParticipationResult;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.entity.SeasonParticipation;
import com.nidus.twinly.season.event.SeasonChangedEvent;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import com.nidus.twinly.season.writer.SeasonParticipationWriter;
import com.nidus.twinly.purchase.service.PurchaseService;
import com.nidus.twinly.season.repository.SeasonRepository;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
import static org.mockito.Mockito.inOrder;
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
    SeasonParticipationWriter seasonParticipationWriter;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Mock
    PurchaseService purchaseService;

    @Mock
    EntitlementReader entitlementReader;

    @Mock
    ConsentReader consentReader;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    SeasonService seasonService;

    @Test
    @DisplayName("결제 권한과 필수 약관 동의를 모두 갖추면 현재 시즌 참가를 위임한다")
    void participateIn_participates_when_eligible() {
        // given: 유저가 있고 권한·필수 약관 동의를 모두 갖춤
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(entitlementReader.hasSimulationAccess(USER_ID)).willReturn(true);
        given(consentReader.hasAgreedAllRequired(USER_ID, PolicyKind.PARALLEL_ENTRY)).willReturn(true);

        // when: 시즌 참여
        seasonService.participateIn(USER_ID);

        // then: 현재 시즌 참가를 writer 에 위임 (upsert 라 재호출해도 최초 참가 시각이 유지된다)
        then(seasonParticipationWriter).should().participateInCurrentSeason(USER_ID);
    }

    @Test
    @DisplayName("결제 직후 아직 반영되지 않은 권한을 구제하도록 권한 확인 전에 RevenueCat 과 동기화한다")
    void participateIn_syncs_purchases_before_checking_access() {
        // given: 참여 조건을 모두 갖춘 유저
        User user = user();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(entitlementReader.hasSimulationAccess(USER_ID)).willReturn(true);
        given(consentReader.hasAgreedAllRequired(USER_ID, PolicyKind.PARALLEL_ENTRY)).willReturn(true);

        // when: 시즌 참여
        seasonService.participateIn(USER_ID);

        // then: 동기화가 권한 확인보다 먼저 수행됨 (결제 직후 웹훅이 늦어도 참여가 막히지 않는다)
        InOrder inOrder = inOrder(purchaseService, entitlementReader);
        inOrder.verify(purchaseService).syncQuietly(user);
        inOrder.verify(entitlementReader).hasSimulationAccess(USER_ID);
    }

    @Test
    @DisplayName("유저가 없으면 USER_NOT_FOUND 예외가 발생하고 동기화도 참가도 하지 않는다")
    void participateIn_when_user_missing_throws() {
        // given: 토큰은 유효하지만 유저가 사라진 상태
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        // when & then: 없는 유저로 외부 동기화를 부르지 않는다
        assertThatThrownBy(() -> seasonService.participateIn(USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

        then(purchaseService).should(never()).syncQuietly(any());
        then(seasonParticipationWriter).should(never()).participateInCurrentSeason(any());
    }

    @Test
    @DisplayName("동기화 후에도 결제 권한이 없으면 SIMULATION_ACCESS_REQUIRED 예외가 발생하고 동의 여부를 보지 않는다")
    void participateIn_without_access_throws() {
        // given: 동기화해도 권한이 붙지 않는 유저
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(entitlementReader.hasSimulationAccess(USER_ID)).willReturn(false);

        // when & then: 권한 부족으로 거절
        assertThatThrownBy(() -> seasonService.participateIn(USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SIMULATION_ACCESS_REQUIRED);

        then(consentReader).should(never()).hasAgreedAllRequired(any(), any());
        then(seasonParticipationWriter).should(never()).participateInCurrentSeason(any());
    }

    @Test
    @DisplayName("결제했어도 필수 약관에 동의하지 않았으면 SIMULATION_CONSENT_REQUIRED 예외가 발생하고 참가시키지 않는다")
    void participateIn_without_consent_throws() {
        // given: 권한은 있지만 평행우주 입장 필수 약관에 동의하지 않은 유저
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(entitlementReader.hasSimulationAccess(USER_ID)).willReturn(true);
        given(consentReader.hasAgreedAllRequired(USER_ID, PolicyKind.PARALLEL_ENTRY)).willReturn(false);

        // when & then: 시뮬레이션 대상이 아니므로 참가 행을 만들지 않는다
        assertThatThrownBy(() -> seasonService.participateIn(USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SIMULATION_CONSENT_REQUIRED);

        then(seasonParticipationWriter).should(never()).participateInCurrentSeason(any());
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
    @DisplayName("시즌 전환 시 결제 상태인 유저를 새 시즌에 자동 참가시킨다")
    void changeSeason_participatesPaidUsersInNewSeason() {
        // given: 전환될 새 시즌
        given(seasonRepository.findAllByIsActiveTrue()).willReturn(List.of());
        given(seasonRepository.save(any(Season.class))).willAnswer(invocation -> {
            Season season = invocation.getArgument(0);
            ReflectionTestUtils.setField(season, "id", 77L);
            return season;
        });

        // when
        seasonService.changeSeason(new SeasonChangeCommand(
                Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-12-01T00:00:00Z")));

        // then: 결제 유저는 다시 참가 요청을 하지 않아도 새 시즌 참가가 이어진다
        then(seasonParticipationWriter).should().participateAllEligible(77L);
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
        then(seasonParticipationWriter).should(never()).participateAllEligible(any());
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
                "2000-01-01", "birthHash", "01000000000", "phoneHash", "me@test.com", "emailHash", null, null, null, null);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }
}
