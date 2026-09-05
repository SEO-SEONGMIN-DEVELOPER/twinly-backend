package com.nidus.twinly.purchase.integration;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.purchase.client.RevenueCatClient;
import com.nidus.twinly.simulation.client.SimulationPreloadClient;
import com.nidus.twinly.purchase.client.RevenueCatEntitlement;
import com.nidus.twinly.purchase.entity.UserEntitlement;
import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.purchase.entity.RevenueCatEvent;
import com.nidus.twinly.purchase.repository.RevenueCatEventRepository;
import com.nidus.twinly.purchase.repository.UserEntitlementRepository;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import com.nidus.twinly.season.repository.SeasonRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PurchaseIntegrationTest extends AbstractIntegrationTest {

    private static final String WEBHOOK_SECRET = "Bearer test-revenue-cat-webhook-secret";

    @Autowired
    UserEntitlementRepository userEntitlementRepository;

    @Autowired
    RevenueCatEventRepository revenueCatEventRepository;

    @Autowired
    SeasonRepository seasonRepository;

    @Autowired
    SeasonParticipationRepository seasonParticipationRepository;

    // RevenueCat REST 호출 차단. 웹훅은 트리거일 뿐이고 진짜 상태는 이 응답이 결정한다.
    @MockitoBean
    RevenueCatClient revenueCatClient;

    @MockitoBean
    SimulationPreloadClient simulationPreloadClient;

    @Test
    @DisplayName("웹훅 수신: 실제 시크릿 인증을 통과해 RevenueCat 조회 결과가 user_entitlements 로 저장된다")
    void webhook_saves_entitlements_end_to_end() throws Exception {
        // given: 실제 유저 저장 + RevenueCat 이 premium 권한을 돌려주도록 설정
        User user = saveUser();
        Instant expiresAt = Instant.now().plus(Duration.ofDays(30));
        given(revenueCatClient.entitlements(user.getRevenueCatUserId().toString()))
                .willReturn(List.of(new RevenueCatEntitlement("premium", expiresAt)));

        // when: 해당 유저의 구매 이벤트로 웹훅 호출
        mockMvc.perform(post("/webhook/v1/revenue-cat")
                        .header("Authorization", WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("INITIAL_PURCHASE", user)))
                .andExpect(status().isOk());

        // then: DB에 권한 행이 실제로 생성됨
        List<UserEntitlement> stored = userEntitlementRepository.findAllByUserId(user.getId());
        assertThat(stored).hasSize(1);
        assertThat(stored.getFirst().getEntitlement()).isEqualTo("premium");
    }

    @Test
    @DisplayName("웹훅으로 저장된 권한이 구매 상태 조회 API 응답에 그대로 나온다")
    void webhook_then_purchases_returns_entitlement() throws Exception {
        // given: 실제 유저 저장 후 웹훅으로 premium 권한을 반영
        User user = saveUser();
        given(revenueCatClient.entitlements(anyString()))
                .willReturn(List.of(new RevenueCatEntitlement("premium", Instant.now().plus(Duration.ofDays(30)))));

        mockMvc.perform(post("/webhook/v1/revenue-cat")
                        .header("Authorization", WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("INITIAL_PURCHASE", user)))
                .andExpect(status().isOk());

        // when: 해당 유저의 실제 액세스 토큰으로 구매 상태 조회
        var result = mockMvc.perform(get("/api/v1/me/purchases")
                .header("Authorization", bearer(user.getId())));

        // then: 식별자가 내려오고, 권한은 응답이 아니라 DB 에 반영돼 있다 (유료 API 가 이 값을 본다)
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.revenueCatUserId").value(user.getRevenueCatUserId().toString()));

        assertThat(userEntitlementRepository.findAllByUserId(user.getId()))
                .extracting(UserEntitlement::getEntitlement)
                .containsExactly("premium");
    }

    @Test
    @DisplayName("결제 반영: simulation_access 가 저장되면 현재 시즌 참가 행이 함께 생성된다")
    void webhook_with_simulation_access_participates_in_current_season() throws Exception {
        // given: 진행 중인 시즌과 실제 유저 + RevenueCat 이 simulation_access 를 돌려줌
        Instant now = Instant.now();
        Season season = seasonRepository.save(
                Season.create(now.minus(Duration.ofDays(30)), now.plus(Duration.ofDays(30))));
        User user = saveUser();
        given(revenueCatClient.entitlements(user.getRevenueCatUserId().toString()))
                .willReturn(List.of(new RevenueCatEntitlement(
                        EntitlementReader.SIMULATION_ACCESS, now.plus(Duration.ofDays(30)))));

        // when: 구매 이벤트로 웹훅 호출
        mockMvc.perform(post("/webhook/v1/revenue-cat")
                        .header("Authorization", WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("INITIAL_PURCHASE", user)))
                .andExpect(status().isOk());

        // then: 별도 참가 API 호출 없이 현재 시즌 참가 행이 생긴다 (결제 = 평행우주 입장)
        assertThat(seasonParticipationRepository.findByUserIdAndSeasonId(user.getId(), season.getId())).isPresent();
    }

    @Test
    @DisplayName("환불 반영: RevenueCat 응답에서 빠진 권한은 DB에서 삭제된다")
    void webhook_removes_revoked_entitlement() throws Exception {
        // given: premium 권한이 이미 저장된 유저
        User user = saveUser();
        userEntitlementRepository.save(UserEntitlement.create(
                user.getId(), "premium", Instant.now().plus(Duration.ofDays(30)), Instant.now().minusSeconds(3600)));

        // given: RevenueCat 이 더 이상 그 권한을 돌려주지 않음 (환불)
        given(revenueCatClient.entitlements(anyString())).willReturn(List.of());

        // when: 환불 이벤트로 웹훅 호출
        mockMvc.perform(post("/webhook/v1/revenue-cat")
                        .header("Authorization", WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("CANCELLATION", user)))
                .andExpect(status().isOk());

        // then: DB에서 권한 행이 사라짐
        assertThat(userEntitlementRepository.findAllByUserId(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("웹훅 인증 실패: 시크릿이 없으면 401이고 DB도 바뀌지 않는다")
    void webhook_without_secret_returns_401() throws Exception {
        // given: 실제 유저 저장
        User user = saveUser();

        // when: 시크릿 없이 웹훅 호출
        mockMvc.perform(post("/webhook/v1/revenue-cat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("INITIAL_PURCHASE", user)))
                .andExpect(status().isUnauthorized());

        // then: 권한 행이 생기지 않음
        assertThat(userEntitlementRepository.findAllByUserId(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("구매 상태 조회: 인증 헤더가 없으면 401을 반환한다")
    void purchases_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 구매 상태 조회
        var result = mockMvc.perform(get("/api/v1/me/purchases"));

        // then: 401 반환
        result.andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("웹훅 수신: 이벤트 종류·유저·환경이 revenue_cat_events 에 기록된다")
    void webhook_records_event_row() throws Exception {
        // given: 실제 유저 저장
        User user = saveUser();
        given(revenueCatClient.entitlements(anyString())).willReturn(List.of());

        // when: 최초 결제 이벤트로 웹훅 호출
        mockMvc.perform(post("/webhook/v1/revenue-cat")
                        .header("Authorization", WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("INITIAL_PURCHASE", user)))
                .andExpect(status().isOk());

        // then: 대시보드가 집계할 값이 그대로 한 행으로 남는다
        List<RevenueCatEvent> events = revenueCatEventRepository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getEventId()).isEqualTo("evt_" + user.getId());
        assertThat(events.getFirst().getType()).isEqualTo("INITIAL_PURCHASE");
        assertThat(events.getFirst().getUserId()).isEqualTo(user.getId());
        assertThat(events.getFirst().getEnvironment()).isEqualTo("SANDBOX");
        assertThat(events.getFirst().getReceivedAt()).isNotNull();
        assertThat(events.getFirst().getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("웹훅 재전송: 같은 event_id 는 한 번만 기록되고 다시 동기화하지 않는다")
    void webhook_with_duplicate_event_id_is_processed_once() throws Exception {
        // given: premium 권한을 돌려주는 유저에게 웹훅이 한 번 도착해 반영된 상태
        User user = saveUser();
        given(revenueCatClient.entitlements(anyString()))
                .willReturn(List.of(new RevenueCatEntitlement("premium", Instant.now().plus(Duration.ofDays(30)))));

        mockMvc.perform(post("/webhook/v1/revenue-cat")
                        .header("Authorization", WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("INITIAL_PURCHASE", user)))
                .andExpect(status().isOk());

        // when: RevenueCat 이 같은 이벤트를 재전송
        mockMvc.perform(post("/webhook/v1/revenue-cat")
                        .header("Authorization", WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("INITIAL_PURCHASE", user)))
                .andExpect(status().isOk());

        // then: 행은 하나뿐이고 RevenueCat 재조회도 일어나지 않는다 (집계가 부풀지 않는다)
        assertThat(revenueCatEventRepository.findAll()).hasSize(1);
        then(revenueCatClient).should(times(1)).entitlements(user.getRevenueCatUserId().toString());
    }

    @Test
    @DisplayName("동기화 실패: 502 로 응답하고 완료 표시가 없어, 재전송되면 같은 행에서 다시 처리된다")
    void webhook_retries_incomplete_event_on_redelivery() throws Exception {
        // given: 첫 수신에서 RevenueCat 조회가 실패하는 상황
        User user = saveUser();
        willThrow(new BusinessException(ErrorCode.REVENUE_CAT_SYNC_FAILED))
                .given(revenueCatClient).entitlements(anyString());

        // when: 웹훅 최초 수신
        mockMvc.perform(post("/webhook/v1/revenue-cat")
                        .header("Authorization", WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("INITIAL_PURCHASE", user)))
                .andExpect(status().isBadGateway());

        // then: 수신 사실은 남되 완료 표시가 없다 (2xx 가 아니므로 RevenueCat 이 재전송한다)
        List<RevenueCatEvent> afterFailure = revenueCatEventRepository.findAll();
        assertThat(afterFailure).hasSize(1);
        assertThat(afterFailure.getFirst().getCompletedAt()).isNull();

        // when: RevenueCat 이 복구된 뒤 같은 이벤트를 재전송
        willReturn(List.of(new RevenueCatEntitlement("premium", Instant.now().plus(Duration.ofDays(30)))))
                .given(revenueCatClient).entitlements(anyString());

        mockMvc.perform(post("/webhook/v1/revenue-cat")
                        .header("Authorization", WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("INITIAL_PURCHASE", user)))
                .andExpect(status().isOk());

        // then: 행은 하나 그대로이고 이번엔 완료 표시가 찍히며 권한도 반영된다
        List<RevenueCatEvent> afterRetry = revenueCatEventRepository.findAll();
        assertThat(afterRetry).hasSize(1);
        assertThat(afterRetry.getFirst().getCompletedAt()).isNotNull();
        assertThat(userEntitlementRepository.findAllByUserId(user.getId()))
                .extracting(UserEntitlement::getEntitlement)
                .containsExactly("premium");
    }

    private String payload(String type, User user) {
        return """
                {
                  "api_version": "1.0",
                  "event": {
                    "id": "evt_%s",
                    "type": "%s",
                    "app_user_id": "%s",
                    "environment": "SANDBOX"
                  }
                }
                """.formatted(user.getId(), type, user.getRevenueCatUserId());
    }
}
