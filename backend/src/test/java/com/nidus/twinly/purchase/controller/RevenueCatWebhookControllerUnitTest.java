package com.nidus.twinly.purchase.controller;

import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.common.security.SecurityConfig;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.purchase.dto.command.RevenueCatWebhookCommand;
import com.nidus.twinly.purchase.service.PurchaseService;
import com.nidus.twinly.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RevenueCatWebhookController.class)
@Import(SecurityConfig.class)
class RevenueCatWebhookControllerUnitTest {

    private static final String WEBHOOK_SECRET = "Bearer test-revenue-cat-webhook-secret";

    private static final String PAYLOAD = """
            {
              "api_version": "1.0",
              "event": {
                "id": "evt_1",
                "type": "INITIAL_PURCHASE",
                "app_user_id": "0f8c1e2a-4b7d-4c31-9a6e-2f5b8c0d1e34",
                "environment": "SANDBOX",
                "product_id": "twinly_basic_monthly",
                "price": 9.99
              }
            }
            """;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PurchaseService purchaseService;

    @MockitoBean
    UserService userService;

    @MockitoBean
    AnonService anonService;

    @Test
    @DisplayName("시크릿이 일치하면 200을 반환하고 payload에서 뽑은 값으로 서비스를 호출한다")
    void webhook_success() throws Exception {
        // when: 올바른 시크릿으로 웹훅 호출
        var result = mockMvc.perform(post("/webhook/v1/revenue-cat")
                .header("Authorization", WEBHOOK_SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(PAYLOAD));

        // then: 200 반환 + 이벤트 식별자·유저·환경이 커맨드로 전달됨
        result.andExpect(status().isOk());

        ArgumentCaptor<RevenueCatWebhookCommand> captor = ArgumentCaptor.forClass(RevenueCatWebhookCommand.class);
        then(purchaseService).should().receiveWebhook(captor.capture());

        RevenueCatWebhookCommand command = captor.getValue();
        assertThat(command.eventId()).isEqualTo("evt_1");
        assertThat(command.type()).isEqualTo("INITIAL_PURCHASE");
        assertThat(command.environment()).isEqualTo("SANDBOX");
        assertThat(command.appUserIds()).containsExactly("0f8c1e2a-4b7d-4c31-9a6e-2f5b8c0d1e34");
    }

    @Test
    @DisplayName("모르는 필드가 섞여 와도 파싱에 실패하지 않고 200을 반환한다")
    void webhook_with_unknown_fields_succeeds() throws Exception {
        // given: 우리가 읽지 않는 필드가 잔뜩 들어있는 payload
        String payload = """
                {
                  "api_version": "1.0",
                  "event": {
                    "id": "evt_2",
                    "type": "RENEWAL",
                    "app_user_id": "0f8c1e2a-4b7d-4c31-9a6e-2f5b8c0d1e34",
                    "environment": "SANDBOX",
                    "brand_new_field_from_revenuecat": {"nested": [1, 2, 3]},
                    "takehome_percentage": 0.7
                  }
                }
                """;

        // when: 해당 payload 로 웹훅 호출
        var result = mockMvc.perform(post("/webhook/v1/revenue-cat")
                .header("Authorization", WEBHOOK_SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload));

        // then: 200 반환 + 서비스로 위임
        result.andExpect(status().isOk());
        then(purchaseService).should().receiveWebhook(any());
    }

    @Test
    @DisplayName("TRANSFER 처럼 app_user_id 가 없어도 transferred_from·to 를 모두 커맨드에 담는다")
    void webhook_transfer_collects_both_sides() throws Exception {
        // given: app_user_id 없이 이전 출발지·도착지만 담긴 TRANSFER payload
        String payload = """
                {
                  "api_version": "1.0",
                  "event": {
                    "id": "evt_3",
                    "type": "TRANSFER",
                    "transferred_from": ["11111111-1111-4111-8111-111111111111"],
                    "transferred_to": ["22222222-2222-4222-8222-222222222222"]
                  }
                }
                """;

        // when: TRANSFER payload 로 웹훅 호출
        var result = mockMvc.perform(post("/webhook/v1/revenue-cat")
                .header("Authorization", WEBHOOK_SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload));

        // then: 200 반환 + 양쪽 유저가 모두 커맨드에 담김
        result.andExpect(status().isOk());

        ArgumentCaptor<RevenueCatWebhookCommand> captor = ArgumentCaptor.forClass(RevenueCatWebhookCommand.class);
        then(purchaseService).should().receiveWebhook(captor.capture());

        assertThat(captor.getValue().appUserIds()).containsExactlyInAnyOrder(
                "11111111-1111-4111-8111-111111111111",
                "22222222-2222-4222-8222-222222222222");
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void webhook_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 웹훅 호출
        var result = mockMvc.perform(post("/webhook/v1/revenue-cat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(PAYLOAD));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized());
        then(purchaseService).should(never()).receiveWebhook(any());
    }

    @Test
    @DisplayName("시크릿이 틀리면 401을 반환하고 서비스를 호출하지 않는다")
    void webhook_with_wrong_secret_returns_401() throws Exception {
        // given: 웹훅 시크릿은 액세스 토큰으로도 해석되지 않는다
        given(userService.resolveByAccessToken(anyString()))
                .willThrow(new BusinessException(ErrorCode.INVALID_TOKEN));

        // when: 잘못된 시크릿으로 웹훅 호출
        var result = mockMvc.perform(post("/webhook/v1/revenue-cat")
                .header("Authorization", "Bearer wrong-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(PAYLOAD));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized());
        then(purchaseService).should(never()).receiveWebhook(any());
    }
}
