package com.nidus.twinly.invite.service;

import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.invite.client.AppStoreServerClient;
import com.nidus.twinly.invite.client.AppStoreTransaction;
import com.nidus.twinly.invite.config.AppStoreProperties;
import com.nidus.twinly.invite.domain.InviteRewardMethod;
import com.nidus.twinly.invite.domain.InviteRewardStatus;
import com.nidus.twinly.invite.domain.InviteRewardWaitingReason;
import com.nidus.twinly.invite.domain.RewardDecision;
import com.nidus.twinly.invite.entity.InviteReward;
import com.nidus.twinly.invite.repository.InviteRegistrationRepository;
import com.nidus.twinly.invite.repository.InviteRewardRepository;
import com.nidus.twinly.invite.writer.InviteRewardWriter;
import com.nidus.twinly.purchase.client.RevenueCatClient;
import com.nidus.twinly.purchase.client.RevenueCatSubscription;
import com.nidus.twinly.purchase.client.RevenueCatSubscriptions;
import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class InviteRewardGrantServiceUnitTest {

    private static final Long REWARD_ID = 10L;
    private static final Long USER_ID = 1L;
    private static final UUID REVENUE_CAT_USER_ID = UUID.fromString("0f8c1e2a-4b7d-4c31-9a6e-2f5b8c0d1e34");
    private static final String APP_USER_ID = REVENUE_CAT_USER_ID.toString();
    private static final String PLAY_PRODUCT = "twinly_monthly:monthly";
    private static final String OFFER_ID = "invite_reward_1m";

    @Mock
    InviteRewardRepository inviteRewardRepository;

    @Mock
    InviteRegistrationRepository inviteRegistrationRepository;

    @Mock
    InviteRewardWriter inviteRewardWriter;

    @Mock
    UserRepository userRepository;

    @Mock
    RevenueCatClient revenueCatClient;

    @Mock
    AppStoreServerClient appStoreServerClient;

    InviteRewardGrantService inviteRewardGrantService;

    @BeforeEach
    void setUp() {
        inviteRewardGrantService = new InviteRewardGrantService(
                inviteRewardRepository, inviteRegistrationRepository, inviteRewardWriter, userRepository,
                revenueCatClient, appStoreServerClient,
                new AppStoreProperties("com.nidus.twinly", "issuer", "KEY123", "pem", OFFER_ID));
    }

    @Test
    @DisplayName("유료 구독이 없으면 지금과 현재 권한 만료 중 늦은 때부터 30일을 목표로 기록한 뒤 무료 지급한다")
    void process_without_paid_grants_promotional_from_current_expiry() {
        // given: 선착순 무료 권한이 10일 남은 유저
        Instant entitlementExpiresAt = Instant.now().plus(Duration.ofDays(10));
        Instant target = entitlementExpiresAt.plus(Duration.ofDays(30));
        given(inviteRewardRepository.findById(REWARD_ID)).willReturn(
                Optional.of(reward(InviteRewardStatus.WAITING)),
                Optional.of(granting(InviteRewardMethod.PROMOTIONAL, null, entitlementExpiresAt, target, false)));
        givenUser();
        givenSubscriptions(new RevenueCatSubscriptions(entitlementExpiresAt, List.of(promotional(entitlementExpiresAt))));
        given(inviteRewardWriter.startGranting(eq(REWARD_ID), any(), any())).willReturn(true);
        given(inviteRewardWriter.markAttempted(eq(REWARD_ID), eq(0), any())).willReturn(true);

        // when: 처리
        inviteRewardGrantService.process(REWARD_ID);

        // then: 목표 시각을 기록한 결정 + 그 시각으로 무료 지급 + 지급 완료
        ArgumentCaptor<RewardDecision> decision = ArgumentCaptor.forClass(RewardDecision.class);
        then(inviteRewardWriter).should().startGranting(eq(REWARD_ID), decision.capture(), any());
        assertThat(decision.getValue().kind()).isEqualTo(RewardDecision.Kind.PROMOTIONAL);
        assertThat(decision.getValue().targetExpiresAt()).isEqualTo(target);
        then(revenueCatClient).should().grantPromotional(APP_USER_ID, EntitlementReader.SIMULATION_ACCESS, target);
        then(inviteRewardWriter).should().markGranted(eq(REWARD_ID), eq(InviteRewardMethod.PROMOTIONAL), eq(target), any());
    }

    @Test
    @DisplayName("Google Play 유료 구독(자동 갱신)이면 구독 만료 시각 + 30일로 결제를 미룬다")
    void process_play_paid_defers() {
        // given: 6일 뒤 갱신될 Play 구독
        Instant expiresAt = Instant.now().plus(Duration.ofDays(6));
        Instant target = expiresAt.plus(Duration.ofDays(30));
        given(inviteRewardRepository.findById(REWARD_ID)).willReturn(
                Optional.of(reward(InviteRewardStatus.WAITING)),
                Optional.of(granting(InviteRewardMethod.PLAY_DEFER, PLAY_PRODUCT, expiresAt, target, false)));
        givenUser();
        givenSubscriptions(new RevenueCatSubscriptions(expiresAt, List.of(paid(RevenueCatSubscription.PLAY_STORE, PLAY_PRODUCT, "normal", expiresAt, null))));
        given(inviteRewardWriter.startGranting(eq(REWARD_ID), any(), any())).willReturn(true);
        given(inviteRewardWriter.markAttempted(eq(REWARD_ID), eq(0), any())).willReturn(true);

        // when: 처리
        inviteRewardGrantService.process(REWARD_ID);

        // then: 같은 상품의 결제를 목표 시각으로 미루고 지급 완료
        then(revenueCatClient).should().defer(APP_USER_ID, PLAY_PRODUCT, target);
        then(inviteRewardWriter).should().markGranted(eq(REWARD_ID), eq(InviteRewardMethod.PLAY_DEFER), eq(target), any());
    }

    @Test
    @DisplayName("App Store 유료 구독(자동 갱신)이면 서버가 지급하지 않고 애플 오퍼 대기로 바꾼다")
    void process_app_store_paid_pends_apple_offer() {
        // given: App Store 유료 구독
        Instant expiresAt = Instant.now().plus(Duration.ofDays(6));
        given(inviteRewardRepository.findById(REWARD_ID)).willReturn(Optional.of(reward(InviteRewardStatus.WAITING)));
        givenUser();
        givenSubscriptions(new RevenueCatSubscriptions(expiresAt, List.of(paid(RevenueCatSubscription.APP_STORE, "twinly_monthly", "normal", expiresAt, null))));

        // when: 처리
        inviteRewardGrantService.process(REWARD_ID);

        // then: 오퍼 대기 + 외부 지급 없음
        then(inviteRewardWriter).should().pendAppleOffer(eq(REWARD_ID), eq("twinly_monthly"), any());
        then(inviteRewardWriter).should(never()).startGranting(anyLong(), any(), any());
        then(revenueCatClient).should(never()).grantPromotional(any(), any(), any());
    }

    @Test
    @DisplayName("스토어 무료 체험 중이면 storeTrial 로 기다린다")
    void process_store_trial_waits() {
        // given: Play 무료 체험 중
        Instant expiresAt = Instant.now().plus(Duration.ofDays(3));
        given(inviteRewardRepository.findById(REWARD_ID)).willReturn(Optional.of(reward(InviteRewardStatus.WAITING)));
        givenUser();
        givenSubscriptions(new RevenueCatSubscriptions(expiresAt, List.of(paid(RevenueCatSubscription.PLAY_STORE, PLAY_PRODUCT, "trial", expiresAt, null))));

        // when: 처리
        inviteRewardGrantService.process(REWARD_ID);

        // then: storeTrial 대기
        then(inviteRewardWriter).should().waitFor(eq(REWARD_ID), eq(InviteRewardWaitingReason.STORE_TRIAL), any());
    }

    @Test
    @DisplayName("검수 케이스: 11-10 Play 구독을 12-10 으로 미룬 응답이 유실돼 재처리해도 만료일은 12-10 그대로이고 다시 미루지 않는다")
    void continueGranting_after_lost_response_marks_granted_without_second_defer() {
        // given: 목표 12-10 을 기록하고 한 번 시도함, RevenueCat 에는 이미 12-10 이 반영됨
        Instant base = Instant.parse("2026-11-10T00:00:00Z");
        Instant target = Instant.parse("2026-12-10T00:00:00Z");
        given(inviteRewardRepository.findById(REWARD_ID)).willReturn(Optional.of(granting(InviteRewardMethod.PLAY_DEFER, PLAY_PRODUCT, base, target, true)));
        givenUser();
        givenSubscriptions(new RevenueCatSubscriptions(target, List.of(paid(RevenueCatSubscription.PLAY_STORE, PLAY_PRODUCT, "normal", target,
                Instant.parse("2026-10-10T00:00:00Z")))));

        // when: 같은 보상 건 재처리
        inviteRewardGrantService.process(REWARD_ID);

        // then: 추가 미루기 없이 12-10 으로 지급 완료
        then(revenueCatClient).should(never()).defer(any(), any(), any());
        then(inviteRewardWriter).should(never()).markAttempted(anyLong(), anyInt(), any());
        then(inviteRewardWriter).should().markGranted(eq(REWARD_ID), eq(InviteRewardMethod.PLAY_DEFER), eq(target), any());
    }

    @Test
    @DisplayName("이전 시도가 적용되지 않은 것이 확인되면 처음 기록한 목표 시각 그대로 다시 미룬다")
    void continueGranting_not_applied_retries_with_same_target() {
        // given: 시도는 했지만 만료일이 여전히 기준 시각
        Instant base = Instant.parse("2026-11-10T00:00:00Z");
        Instant target = Instant.parse("2026-12-10T00:00:00Z");
        given(inviteRewardRepository.findById(REWARD_ID)).willReturn(Optional.of(granting(InviteRewardMethod.PLAY_DEFER, PLAY_PRODUCT, base, target, true)));
        givenUser();
        givenSubscriptions(new RevenueCatSubscriptions(base, List.of(paid(RevenueCatSubscription.PLAY_STORE, PLAY_PRODUCT, "normal", base,
                Instant.parse("2026-10-10T00:00:00Z")))));
        given(inviteRewardWriter.markAttempted(eq(REWARD_ID), eq(1), any())).willReturn(true);

        // when: 재처리
        inviteRewardGrantService.process(REWARD_ID);

        // then: 30일을 다시 더하지 않고 12-10 으로 재요청
        then(revenueCatClient).should().defer(APP_USER_ID, PLAY_PRODUCT, target);
    }

    @Test
    @DisplayName("정상 갱신으로 만료일이 바뀌어 적용 여부를 구분할 수 없으면 보류하고 추가 지급하지 않는다")
    void continueGranting_ambiguous_holds() {
        // given: 시도 후 기준 시각에 정상 갱신이 일어남
        Instant base = Instant.parse("2026-11-10T00:00:00Z");
        Instant target = Instant.parse("2026-12-10T00:00:00Z");
        given(inviteRewardRepository.findById(REWARD_ID)).willReturn(Optional.of(granting(InviteRewardMethod.PLAY_DEFER, PLAY_PRODUCT, base, target, true)));
        givenUser();
        givenSubscriptions(new RevenueCatSubscriptions(target, List.of(paid(RevenueCatSubscription.PLAY_STORE, PLAY_PRODUCT, "normal", target, base))));

        // when: 재처리
        inviteRewardGrantService.process(REWARD_ID);

        // then: 보류, 지급 완료·추가 요청 없음
        then(inviteRewardWriter).should().hold(eq(REWARD_ID), any());
        then(inviteRewardWriter).should(never()).markGranted(anyLong(), any(), any(), any());
        then(revenueCatClient).should(never()).defer(any(), any(), any());
    }

    @Test
    @DisplayName("다른 처리가 먼저 시도 횟수를 올렸으면 외부 요청을 보내지 않는다 (동시 처리)")
    void continueGranting_lost_race_skips_call() {
        // given: 첫 시도 직전, 시도 기록 경합에서 짐
        Instant target = Instant.now().plus(Duration.ofDays(30));
        given(inviteRewardRepository.findById(REWARD_ID)).willReturn(Optional.of(granting(InviteRewardMethod.PROMOTIONAL, null, Instant.now(), target, false)));
        givenUser();
        given(inviteRewardWriter.markAttempted(eq(REWARD_ID), eq(0), any())).willReturn(false);

        // when: 처리
        inviteRewardGrantService.process(REWARD_ID);

        // then: 외부 요청·완료 기록 없음
        then(revenueCatClient).should(never()).grantPromotional(any(), any(), any());
        then(inviteRewardWriter).should(never()).markGranted(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("애플 오퍼 수락 뒤 무료 1개월이 시작된 거래가 보이면 그 만료 시각으로 지급 완료한다")
    void process_accepted_apple_offer_started_marks_granted() {
        // given: 수락 상태 + App Store 에 오퍼가 적용된 거래
        InviteReward accepted = reward(InviteRewardStatus.APPLE_OFFER_ACCEPTED);
        ReflectionTestUtils.setField(accepted, "appleTransactionId", "2000000123");
        Instant endsAt = Instant.parse("2026-12-20T00:00:00Z");
        given(inviteRewardRepository.findById(REWARD_ID)).willReturn(Optional.of(accepted));
        givenUser();
        given(appStoreServerClient.latestTransactions("2000000123")).willReturn(List.of(
                new AppStoreTransaction("twinly_monthly", 2, OFFER_ID, Instant.parse("2026-11-20T00:00:00Z"), endsAt)));

        // when: 처리
        inviteRewardGrantService.process(REWARD_ID);

        // then: appleOffer 지급 완료, RevenueCat 판정 안 함
        then(inviteRewardWriter).should().markGranted(eq(REWARD_ID), eq(InviteRewardMethod.APPLE_OFFER), eq(endsAt), any());
        verifyNoInteractions(revenueCatClient);
    }

    @Test
    @DisplayName("애플 오퍼 수락 뒤 시작 전에 자동 갱신을 끄면 무료 지급으로 바꾼다")
    void process_accepted_then_auto_renew_off_switches_to_promotional() {
        // given: 수락 상태, 오퍼 미적용, 자동 갱신 꺼진 App Store 구독
        InviteReward accepted = reward(InviteRewardStatus.APPLE_OFFER_ACCEPTED);
        ReflectionTestUtils.setField(accepted, "appleTransactionId", "2000000123");
        Instant expiresAt = Instant.now().plus(Duration.ofDays(6));
        given(inviteRewardRepository.findById(REWARD_ID)).willReturn(
                Optional.of(accepted),
                Optional.of(granting(InviteRewardMethod.PROMOTIONAL, null, expiresAt, expiresAt.plus(Duration.ofDays(30)), false)));
        givenUser();
        given(appStoreServerClient.latestTransactions("2000000123")).willReturn(List.of(
                new AppStoreTransaction("twinly_monthly", null, null, Instant.now().minus(Duration.ofDays(24)), expiresAt)));
        givenSubscriptions(new RevenueCatSubscriptions(expiresAt, List.of(new RevenueCatSubscription(
                "twinly_monthly", RevenueCatSubscription.APP_STORE, "normal", null, expiresAt, Instant.now(), null, null))));
        given(inviteRewardWriter.startGranting(eq(REWARD_ID), any(), any())).willReturn(true);
        given(inviteRewardWriter.markAttempted(eq(REWARD_ID), eq(0), any())).willReturn(true);

        // when: 처리
        inviteRewardGrantService.process(REWARD_ID);

        // then: 구독 만료 + 30일로 무료 지급
        then(revenueCatClient).should().grantPromotional(APP_USER_ID, EntitlementReader.SIMULATION_ACCESS, expiresAt.plus(Duration.ofDays(30)));
    }

    @Test
    @DisplayName("이미 지급된 보상은 아무것도 하지 않는다")
    void process_granted_is_noop() {
        // given: 지급 완료
        given(inviteRewardRepository.findById(REWARD_ID)).willReturn(Optional.of(reward(InviteRewardStatus.GRANTED)));

        // when: 처리
        inviteRewardGrantService.process(REWARD_ID);

        // then: 외부 호출·상태 변경 없음
        verifyNoInteractions(revenueCatClient, inviteRewardWriter);
    }

    private void givenUser() {
        User user = User.create(
                "nick", "홍", "familyHash", "길동", "givenHash",
                Gender.MALE, "organization", "organizationHash", "니두스", "affHash", "2020123", "affNoHash",
                "2000-01-01", "birthHash", "01000000000", "phoneHash", "me@test.com", "emailHash", "di", "di-hash", null, null);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        ReflectionTestUtils.setField(user, "revenueCatUserId", REVENUE_CAT_USER_ID);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
    }

    private void givenSubscriptions(RevenueCatSubscriptions subscriptions) {
        given(revenueCatClient.subscriptions(APP_USER_ID, EntitlementReader.SIMULATION_ACCESS)).willReturn(subscriptions);
    }

    private InviteReward reward(InviteRewardStatus status) {
        InviteReward reward = InviteReward.create(USER_ID, "di-hash", Instant.now());
        ReflectionTestUtils.setField(reward, "id", REWARD_ID);
        ReflectionTestUtils.setField(reward, "status", status);
        return reward;
    }

    private InviteReward granting(InviteRewardMethod method, String productId, Instant base, Instant target, boolean attempted) {
        InviteReward reward = reward(InviteRewardStatus.WAITING);
        ReflectionTestUtils.setField(reward, "method", method);
        ReflectionTestUtils.setField(reward, "productId", productId);
        ReflectionTestUtils.setField(reward, "baseExpiresAt", base);
        ReflectionTestUtils.setField(reward, "targetExpiresAt", target);
        if (attempted) {
            ReflectionTestUtils.setField(reward, "attemptCount", 1);
            ReflectionTestUtils.setField(reward, "attemptedAt", Instant.now());
        }
        return reward;
    }

    private RevenueCatSubscription paid(String store, String productId, String periodType, Instant expiresAt, Instant purchasedAt) {
        return new RevenueCatSubscription(productId, store, periodType, purchasedAt, expiresAt, null, null, null);
    }

    private RevenueCatSubscription promotional(Instant expiresAt) {
        return new RevenueCatSubscription("rc_promo_simulation_access_custom", RevenueCatSubscription.PROMOTIONAL, "promotional", null, expiresAt, null, null, null);
    }
}
