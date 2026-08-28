package com.nidus.twinly.purchase.service;

import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.purchase.RevenueCatProperties;
import com.nidus.twinly.purchase.client.RevenueCatClient;
import com.nidus.twinly.purchase.client.RevenueCatEntitlement;
import com.nidus.twinly.purchase.domain.RevenueCatEnvironment;
import com.nidus.twinly.purchase.dto.command.RevenueCatWebhookCommand;
import com.nidus.twinly.purchase.writer.UserEntitlementWriter;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    UserEntitlementWriter userEntitlementWriter;

    PurchaseService purchaseService;

    @BeforeEach
    void setUp() {
        RevenueCatProperties properties = new RevenueCatProperties("secret", "sk_test", RevenueCatEnvironment.SANDBOX);
        purchaseService = new PurchaseService(properties, revenueCatClient, userRepository, userEntitlementWriter);
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
        then(userEntitlementWriter).should().replaceAll(eq(USER_ID), eq(entitlements), any());
    }

    @Test
    @DisplayName("환경이 다르면 RevenueCat 을 조회하지도, 권한을 건드리지도 않는다")
    void receiveWebhook_with_other_environment_is_ignored() {
        // when: SANDBOX 서버에 PRODUCTION 이벤트가 도착
        purchaseService.receiveWebhook(command("RENEWAL", "PRODUCTION", List.of(APP_USER_ID)));

        // then: 외부 조회도 교체도 일어나지 않음
        then(revenueCatClient).should(never()).entitlements(anyString());
        then(userEntitlementWriter).should(never()).replaceAll(anyLong(), anyList(), any());
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
        then(userEntitlementWriter).should().replaceAll(anyLong(), anyList(), any());
    }

    @Test
    @DisplayName("이전 이벤트는 출발지·도착지 유저를 각각 동기화한다")
    void receiveWebhook_transfer_syncs_both_users() {
        // given: 두 유저 모두 우리 유저
        UUID from = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID to = UUID.fromString("22222222-2222-4222-8222-222222222222");
        given(userRepository.findByRevenueCatUserId(from)).willReturn(Optional.of(user(10L)));
        given(userRepository.findByRevenueCatUserId(to)).willReturn(Optional.of(user(20L)));
        given(revenueCatClient.entitlements(anyString())).willReturn(List.of());

        // when: TRANSFER 이벤트 수신
        purchaseService.receiveWebhook(command("TRANSFER", "SANDBOX", List.of(from.toString(), to.toString())));

        // then: 두 유저 모두 조회되고 각각 교체 위임
        then(revenueCatClient).should().entitlements(from.toString());
        then(revenueCatClient).should().entitlements(to.toString());
        then(userEntitlementWriter).should().replaceAll(eq(10L), anyList(), any());
        then(userEntitlementWriter).should().replaceAll(eq(20L), anyList(), any());
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
        then(userEntitlementWriter).should(never()).replaceAll(anyLong(), anyList(), any());
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

    private RevenueCatWebhookCommand command(String type, String environment, List<String> appUserIds) {
        return new RevenueCatWebhookCommand("evt_1", type, appUserIds, environment);
    }

    private User user() {
        return user(USER_ID);
    }

    private User user(Long id) {
        User user = User.create(
                "nick", "홍", "familyHash", "길동", "givenHash",
                Gender.MALE, "organization", "organizationHash", "니두스", "affHash", "2020123", "affNoHash",
                "2000-01-01", "birthHash", "01000000000", "phoneHash", "me@test.com", "emailHash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
