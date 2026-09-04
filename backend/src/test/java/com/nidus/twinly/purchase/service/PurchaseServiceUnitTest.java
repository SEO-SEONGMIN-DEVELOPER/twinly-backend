package com.nidus.twinly.purchase.service;

import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.purchase.RevenueCatProperties;
import com.nidus.twinly.purchase.client.RevenueCatClient;
import com.nidus.twinly.purchase.client.RevenueCatEntitlement;
import com.nidus.twinly.purchase.domain.RevenueCatEnvironment;
import com.nidus.twinly.purchase.dto.command.RevenueCatWebhookCommand;
import com.nidus.twinly.purchase.event.SimulationAccessGrantedEvent;
import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.purchase.writer.PurchaseWriter;
import com.nidus.twinly.season.writer.SeasonParticipationWriter;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceUnitTest {

    private static final UUID REVENUE_CAT_USER_ID = UUID.fromString("0f8c1e2a-4b7d-4c31-9a6e-2f5b8c0d1e34");
    private static final String APP_USER_ID = REVENUE_CAT_USER_ID.toString();
    private static final Long USER_ID = 1L;

    @Mock
    RevenueCatClient revenueCatClient;

    @Mock
    UserRepository userRepository;

    @Mock
    PurchaseWriter purchaseWriter;

    @Mock
    EntitlementReader entitlementReader;

    @Mock
    SeasonParticipationWriter seasonParticipationWriter;

    @Mock
    ApplicationEventPublisher eventPublisher;

    PurchaseService purchaseService;

    @BeforeEach
    void setUp() {
        RevenueCatProperties properties = new RevenueCatProperties("secret", "sk_test", RevenueCatEnvironment.SANDBOX);
        purchaseService = new PurchaseService(
                properties, revenueCatClient, userRepository, purchaseWriter, entitlementReader, seasonParticipationWriter, eventPublisher);
    }

    @Test
    @DisplayName("환경이 일치하면 RevenueCat 에서 받은 권한 목록으로 교체를 위임한다")
    void receiveWebhook_syncs_entitlements() {
        // given: 우리 유저이고 RevenueCat 이 premium 권한을 돌려줌
        Instant expiresAt = Instant.parse("2026-09-30T00:00:00Z");
        List<RevenueCatEntitlement> entitlements = List.of(new RevenueCatEntitlement("premium", expiresAt));
        given(userRepository.findByRevenueCatUserId(REVENUE_CAT_USER_ID)).willReturn(Optional.of(user()));
        given(revenueCatClient.entitlements(APP_USER_ID)).willReturn(entitlements);

        // when: SANDBOX 이벤트 수신
        purchaseService.receiveWebhook(command("RENEWAL", "SANDBOX", List.of(APP_USER_ID)));

        // then: 조회한 권한 목록 그대로 교체 위임
        then(purchaseWriter).should().replaceEntitlements(eq(USER_ID), eq(entitlements), any());
    }

    @Test
    @DisplayName("환경이 다르면 RevenueCat 을 조회하지도, 권한을 건드리지도 않는다")
    void receiveWebhook_with_other_environment_is_ignored() {
        // when: SANDBOX 서버에 PRODUCTION 이벤트가 도착
        purchaseService.receiveWebhook(command("RENEWAL", "PRODUCTION", List.of(APP_USER_ID)));

        // then: 외부 조회도 교체도 일어나지 않음
        then(revenueCatClient).should(never()).entitlements(anyString());
        then(purchaseWriter).should(never()).replaceEntitlements(anyLong(), anyList(), any());
    }

    @Test
    @DisplayName("환경 값이 없는 이벤트는 거르지 않고 동기화한다")
    void receiveWebhook_without_environment_is_processed() {
        // given: 우리 유저이고 권한이 비어 있음
        given(userRepository.findByRevenueCatUserId(REVENUE_CAT_USER_ID)).willReturn(Optional.of(user()));
        given(revenueCatClient.entitlements(APP_USER_ID)).willReturn(List.of());

        // when: environment 가 빠진 이벤트 수신 (TRANSFER 등)
        purchaseService.receiveWebhook(command("TRANSFER", null, List.of(APP_USER_ID)));

        // then: 동기화가 수행됨
        then(purchaseWriter).should().replaceEntitlements(anyLong(), anyList(), any());
    }

    @Test
    @DisplayName("이전 이벤트는 출발지·도착지 유저를 각각 동기화한다")
    void receiveWebhook_transfer_syncs_both_users() {
        // given: 두 유저 모두 우리 유저
        UUID from = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID to = UUID.fromString("22222222-2222-4222-8222-222222222222");
        given(userRepository.findByRevenueCatUserId(from)).willReturn(Optional.of(user(10L, from)));
        given(userRepository.findByRevenueCatUserId(to)).willReturn(Optional.of(user(20L, to)));
        given(revenueCatClient.entitlements(anyString())).willReturn(List.of());

        // when: TRANSFER 이벤트 수신
        purchaseService.receiveWebhook(command("TRANSFER", "SANDBOX", List.of(from.toString(), to.toString())));

        // then: 두 유저 모두 조회되고 각각 교체 위임
        then(revenueCatClient).should().entitlements(from.toString());
        then(revenueCatClient).should().entitlements(to.toString());
        then(purchaseWriter).should().replaceEntitlements(eq(10L), anyList(), any());
        then(purchaseWriter).should().replaceEntitlements(eq(20L), anyList(), any());
    }

    @Test
    @DisplayName("우리 유저가 아니면 RevenueCat 을 조회하지 않는다")
    void receiveWebhook_with_unknown_user_is_ignored() {
        // given: 해당 식별자를 가진 유저가 없음
        given(userRepository.findByRevenueCatUserId(REVENUE_CAT_USER_ID)).willReturn(Optional.empty());

        // when: 이벤트 수신
        purchaseService.receiveWebhook(command("RENEWAL", "SANDBOX", List.of(APP_USER_ID)));

        // then: 외부 조회도 교체도 일어나지 않음
        then(revenueCatClient).should(never()).entitlements(anyString());
        then(purchaseWriter).should(never()).replaceEntitlements(anyLong(), anyList(), any());
    }

    @Test
    @DisplayName("app_user_id 가 UUID 형식이 아니면 조회 없이 무시한다")
    void receiveWebhook_with_non_uuid_app_user_id_is_ignored() {
        // when: 익명 식별자처럼 UUID 가 아닌 값이 담긴 이벤트 수신
        purchaseService.receiveWebhook(command("RENEWAL", "SANDBOX", List.of("$RCAnonymousID:abc123")));

        // then: 유저 조회조차 시도하지 않음
        then(userRepository).should(never()).findByRevenueCatUserId(any());
        then(revenueCatClient).should(never()).entitlements(anyString());
    }

    @Test
    @DisplayName("마지막 동기화가 오래됐으면 RevenueCat 을 조회해 권한을 갱신한다")
    void syncIfStale_syncs_when_stale() {
        // given: 한 번도 동기화한 적 없는 유저
        User user = user();
        given(revenueCatClient.entitlements(APP_USER_ID)).willReturn(List.of());

        // when: 조건부 동기화 호출
        purchaseService.syncIfStale(user);

        // then: 시도 시각을 먼저 기록하고 실제 조회까지 수행
        then(purchaseWriter).should().markSyncAttempt(eq(USER_ID), any());
        then(revenueCatClient).should().entitlements(APP_USER_ID);
    }

    @Test
    @DisplayName("최근에 동기화했으면 RevenueCat 을 조회하지 않는다")
    void syncIfStale_skips_when_fresh() {
        // given: 방금 동기화한 유저
        User user = user();
        ReflectionTestUtils.setField(user, "purchasesSyncedAt", Instant.now());

        // when: 조건부 동기화 호출
        purchaseService.syncIfStale(user);

        // then: 외부 조회도 기록도 하지 않음
        then(revenueCatClient).should(never()).entitlements(anyString());
        then(purchaseWriter).should(never()).markSyncAttempt(anyLong(), any());
    }

    @Test
    @DisplayName("동기화가 실패해도 예외를 밖으로 내보내지 않는다")
    void syncIfStale_swallows_failure() {
        // given: RevenueCat 조회가 실패하는 상황
        User user = user();
        given(revenueCatClient.entitlements(APP_USER_ID))
                .willThrow(new BusinessException(ErrorCode.REVENUE_CAT_SYNC_FAILED));

        // when & then: 조회 API 가 동기화 실패로 같이 죽지 않아야 하므로 예외가 전파되지 않는다
        assertThatCode(() -> purchaseService.syncIfStale(user)).doesNotThrowAnyException();
        then(purchaseWriter).should(never()).replaceEntitlements(anyLong(), anyList(), any());
    }

    @Test
    @DisplayName("동기화 결과 simulation_access 가 살아 있으면 현재 시즌에 자동 참가시킨다")
    void sync_participates_in_current_season_when_access_granted() {
        // given: 결제로 simulation_access 를 갖게 된 유저
        User user = user();
        given(revenueCatClient.entitlements(APP_USER_ID))
                .willReturn(List.of(new RevenueCatEntitlement("simulation_access", Instant.parse("2026-09-30T00:00:00Z"))));
        given(entitlementReader.hasSimulationAccess(USER_ID)).willReturn(true);

        // when: 동기화
        purchaseService.sync(user);

        // then: 별도 참가 요청 없이 평행우주(시즌) 참가가 이어진다
        then(seasonParticipationWriter).should().participateInCurrentSeason(USER_ID);
    }

    @Test
    @DisplayName("simulation_access 가 없으면 시즌 참가를 만들지 않는다")
    void sync_does_not_participate_without_access() {
        // given: 결제 권한이 없는 유저
        User user = user();
        given(revenueCatClient.entitlements(APP_USER_ID)).willReturn(List.of());
        given(entitlementReader.hasSimulationAccess(USER_ID)).willReturn(false);

        // when: 동기화
        purchaseService.sync(user);

        // then: 참가 행을 만들지 않는다
        then(seasonParticipationWriter).should(never()).participateInCurrentSeason(anyLong());
    }

    @Test
    @DisplayName("동기화로 simulation_access 가 없다가 생기면 권한 획득 이벤트를 발행한다")
    void sync_publishes_event_when_access_newly_granted() {
        // given: 동기화 전에는 권한이 없고, 교체 후에 생김
        User user = user();
        given(revenueCatClient.entitlements(APP_USER_ID))
                .willReturn(List.of(new RevenueCatEntitlement("simulation_access", Instant.parse("2026-09-30T00:00:00Z"))));
        given(entitlementReader.hasSimulationAccess(USER_ID)).willReturn(false, true);

        // when: 동기화
        purchaseService.sync(user);

        // then: 이 유저의 권한 획득 이벤트가 반영 시각과 함께 한 번 나간다
        ArgumentCaptor<SimulationAccessGrantedEvent> captor = ArgumentCaptor.forClass(SimulationAccessGrantedEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().grantedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 simulation_access 가 있던 유저의 갱신 동기화에는 이벤트를 발행하지 않는다")
    void sync_does_not_publish_event_when_access_already_held() {
        // given: 동기화 전후 모두 권한 보유 (구독 갱신)
        User user = user();
        given(revenueCatClient.entitlements(APP_USER_ID))
                .willReturn(List.of(new RevenueCatEntitlement("simulation_access", Instant.parse("2026-10-30T00:00:00Z"))));
        given(entitlementReader.hasSimulationAccess(USER_ID)).willReturn(true, true);

        // when: 동기화
        purchaseService.sync(user);

        // then: 시즌 참가는 이어지지만 이벤트는 없다
        then(seasonParticipationWriter).should().participateInCurrentSeason(USER_ID);
        then(eventPublisher).should(never()).publishEvent(any(SimulationAccessGrantedEvent.class));
    }

    private RevenueCatWebhookCommand command(String type, String environment, List<String> appUserIds) {
        return new RevenueCatWebhookCommand("evt_1", type, appUserIds, environment);
    }

    private User user() {
        return user(USER_ID, REVENUE_CAT_USER_ID);
    }

    private User user(Long id, UUID revenueCatUserId) {
        User user = User.create(
                "nick", "홍", "familyHash", "길동", "givenHash",
                Gender.MALE, "organization", "organizationHash", "니두스", "affHash", "2020123", "affNoHash",
                "2000-01-01", "birthHash", "01000000000", "phoneHash", "me@test.com", "emailHash", null, null);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "revenueCatUserId", revenueCatUserId);
        return user;
    }
}
