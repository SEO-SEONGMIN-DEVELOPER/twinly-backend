package com.nidus.twinly.season.service;

import com.nidus.twinly.block.entity.Block;
import com.nidus.twinly.block.repository.BlockRepository;
import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.season.domain.SeasonRecapData;
import com.nidus.twinly.season.domain.SeasonRecapTimeSlot;
import com.nidus.twinly.season.dto.result.SeasonLatestRecapResult;
import com.nidus.twinly.season.entity.SeasonRecap;
import com.nidus.twinly.season.repository.SeasonRecapRepository;
import com.nidus.twinly.user.entity.Photo;
import com.nidus.twinly.user.repository.PhotoRepository;
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
import tools.jackson.databind.ObjectMapper;

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

    @Mock
    SeasonRecapRepository seasonRecapRepository;

    @Mock
    PhotoRepository photoRepository;

    @Mock
    CloudFrontService cloudFrontService;

    @Mock
    BlockRepository blockRepository;

    @Mock
    ObjectMapper objectMapper;

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

    @Test
    @DisplayName("제공 중인 리캡이 없으면 SEASON_RECAP_NOT_FOUND 예외가 발생한다")
    void latestRecap_without_serving_recap_throws() {
        // given: 현재 시즌 기준으로 제공 중인 리캡 없음
        given(currentSeasonReader.read()).willReturn(season(Instant.now(), Instant.now().plus(Duration.ofDays(30))));
        given(seasonRecapRepository.findServingByUserId(USER_ID, CURRENT_SEASON_ID)).willReturn(Optional.empty());

        // when & then: SEASON_RECAP_NOT_FOUND
        assertThatThrownBy(() -> seasonService.latestRecap(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SEASON_RECAP_NOT_FOUND);
    }

    @Test
    @DisplayName("리캡의 숫자는 저장된 값 그대로 주고, 상대는 현재 이름·사진을 붙이되 탈퇴·차단(양방향) 상대는 빼고 다음 순위로 채우지 않는다")
    void latestRecap_maps_saved_numbers_and_hides_withdrawn_or_blocked_partners() {
        // given: 7번 시즌 리캡 — mostMet에 21(정상)·22(탈퇴)·23(내가 차단), closest에 24(나를 차단)·21, bestMatches에 21
        Instant startedAt = Instant.parse("2026-09-30T15:00:00Z");
        Instant endedAt = Instant.parse("2026-10-31T14:59:59Z");
        SeasonRecapData data = new SeasonRecapData(
                startedAt, endedAt, 812, 24,
                new SeasonRecapData.Ranked(2460, 12), new SeasonRecapData.Ranked(9120, 40), new SeasonRecapData.Ranked(0, null),
                new SeasonRecapData.Ranked(1284, 8),
                List.of(new SeasonRecapData.Place("P0123", "학생 거리 카페", 18, 1260)),
                new SeasonRecapData.Ranked(27, 30), new SeasonRecapData.Ranked(6, 21),
                List.of(new SeasonRecapData.MostMet(21L, 12, 40), new SeasonRecapData.MostMet(22L, 10, 30), new SeasonRecapData.MostMet(23L, 9, 20)),
                List.of(new SeasonRecapData.Closest(24L, 80), new SeasonRecapData.Closest(21L, 74)),
                List.of(new SeasonRecapData.BestMatch(21L, 90.0, 10, 9)),
                new SeasonRecapData.Ranked(19, 15),
                new SeasonRecapData.ActivityType(SeasonRecapTimeSlot.EVENING, 14, 23, 54, 26));
        Instant createdAt = Instant.parse("2026-10-31T15:03:12Z");

        given(currentSeasonReader.read()).willReturn(season(Instant.now(), Instant.now().plus(Duration.ofDays(30))));
        given(seasonRecapRepository.findServingByUserId(USER_ID, CURRENT_SEASON_ID))
                .willReturn(Optional.of(SeasonRecap.create(USER_ID, 7L, "recap-json", createdAt)));
        given(objectMapper.readValue("recap-json", SeasonRecapData.class)).willReturn(data);

        User withdrawn = partner(22L);
        withdrawn.requestWithdrawal(Duration.ofDays(15));
        given(userRepository.findAllById(List.of(21L, 22L, 23L, 24L)))
                .willReturn(List.of(partner(21L), withdrawn, partner(23L), partner(24L)));
        given(blockRepository.findAllByUserId(USER_ID)).willReturn(List.of(Block.create(USER_ID, 23L)));
        given(blockRepository.findAllByUserIdInAndBlockedUserId(List.of(21L, 22L, 23L, 24L), USER_ID))
                .willReturn(List.of(Block.create(24L, USER_ID)));
        given(photoRepository.findAllByUserIdInAndType(List.of(21L), PhotoType.PROFILE))
                .willReturn(List.of(Photo.create(21L, PhotoType.PROFILE, "photo-21", 0, 0, 100, 100, Instant.now())));
        given(cloudFrontService.getSignedUrl("photo-21")).willReturn("https://cdn/photo-21");

        // when: 최근 리캡 조회
        SeasonLatestRecapResult result = seasonService.latestRecap(USER_ID);

        // then: 숫자는 저장값 그대로
        assertThat(result.seasonId()).isEqualTo(7L);
        assertThat(result.startedAt()).isEqualTo(startedAt);
        assertThat(result.endedAt()).isEqualTo(endedAt);
        assertThat(result.population()).isEqualTo(812);
        assertThat(result.together().days()).isEqualTo(24);
        assertThat(result.activity().move().minutes()).isZero();
        assertThat(result.activity().move().topPercent()).isNull();
        assertThat(result.bubbles().count()).isEqualTo(1284);
        assertThat(result.places()).hasSize(1);
        assertThat(result.activityType().type()).isEqualTo(SeasonRecapTimeSlot.EVENING);
        assertThat(result.activityType().counts().evening()).isEqualTo(54);

        // then: 탈퇴(22)·내가 차단(23)·나를 차단(24)은 빠지고 남은 21만, 현재 닉네임과 서명 URL 사진으로
        assertThat(result.mostMet()).extracting("userId").containsExactly(21L);
        assertThat(result.mostMet().getFirst().userName()).isEqualTo("nick21");
        assertThat(result.mostMet().getFirst().profilePhoto().photoUrl()).isEqualTo("https://cdn/photo-21");
        assertThat(result.closest()).extracting("userId").containsExactly(21L);
        assertThat(result.bestMatches()).extracting("userId").containsExactly(21L);
        assertThat(result.bestMatches().getFirst().matchRate()).isEqualTo(90.0);
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

    private User partner(Long id) {
        User user = User.create(
                "nick" + id, "홍", "familyHash", "길동", "givenHash",
                Gender.MALE, "organization", "organizationHash", "니두스", "affHash", "2020123", "affNoHash",
                "2000-01-01", "birthHash", "0100000" + id, "phoneHash", id + "@test.com", "emailHash", null, null, null, null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
