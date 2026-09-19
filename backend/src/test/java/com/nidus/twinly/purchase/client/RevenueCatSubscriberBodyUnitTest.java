package com.nidus.twinly.purchase.client;

import com.nidus.twinly.purchase.domain.RevenueCatEnvironment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RevenueCatSubscriberBodyUnitTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private static final String SANDBOX_SUBSCRIPTION = """
            {
              "subscriber": {
                "entitlements": {
                  "simulation_access": {
                    "expires_date": "2026-09-20T10:13:34Z",
                    "product_identifier": "twinly_simulation_monthly"
                  }
                },
                "subscriptions": {
                  "twinly_simulation_monthly": { "is_sandbox": true, "store": "app_store" }
                },
                "non_subscriptions": {}
              }
            }
            """;

    private static final String PRODUCTION_SUBSCRIPTION = """
            {
              "subscriber": {
                "entitlements": {
                  "simulation_access": {
                    "expires_date": "2026-10-19T10:13:34Z",
                    "product_identifier": "twinly_simulation_monthly"
                  }
                },
                "subscriptions": {
                  "twinly_simulation_monthly": { "is_sandbox": false, "store": "app_store" }
                },
                "non_subscriptions": {}
              }
            }
            """;

    @Test
    @DisplayName("prod 는 App Store 심사·TestFlight 의 샌드박스 결제로 생긴 권한도 반영한다")
    void production_keeps_sandbox_entitlement() {
        // given: 샌드박스 구독으로 생긴 권한 (심사관 결제도 샌드박스다)
        RevenueCatSubscriberBody body = read(SANDBOX_SUBSCRIPTION);

        // when
        List<RevenueCatEntitlement> entitlements = body.entitlementsIn(RevenueCatEnvironment.PRODUCTION);

        // then: 버리면 심사관이 결제해도 유료 기능이 열리지 않아 리젝된다
        assertThat(entitlements).containsExactly(
                new RevenueCatEntitlement("simulation_access", Instant.parse("2026-09-20T10:13:34Z")));
    }

    @Test
    @DisplayName("prod 는 실결제로 생긴 권한을 만료 시각과 함께 그대로 반영한다")
    void production_keeps_production_entitlement() {
        // given: 실결제 구독으로 생긴 권한
        RevenueCatSubscriberBody body = read(PRODUCTION_SUBSCRIPTION);

        // when
        List<RevenueCatEntitlement> entitlements = body.entitlementsIn(RevenueCatEnvironment.PRODUCTION);

        // then
        assertThat(entitlements).containsExactly(
                new RevenueCatEntitlement("simulation_access", Instant.parse("2026-10-19T10:13:34Z")));
    }

    @Test
    @DisplayName("stage 는 샌드박스 권한만 반영하고 실결제 권한은 버린다")
    void sandbox_keeps_only_sandbox_entitlement() {
        // given & when
        List<RevenueCatEntitlement> fromSandbox = read(SANDBOX_SUBSCRIPTION).entitlementsIn(RevenueCatEnvironment.SANDBOX);
        List<RevenueCatEntitlement> fromProduction = read(PRODUCTION_SUBSCRIPTION).entitlementsIn(RevenueCatEnvironment.SANDBOX);

        // then: 웹훅의 환경 필터와 같은 대칭 규칙이다
        assertThat(fromSandbox).extracting(RevenueCatEntitlement::entitlement).containsExactly("simulation_access");
        assertThat(fromProduction).isEmpty();
    }

    @Test
    @DisplayName("비구독 상품은 같은 상품의 구매 중 하나라도 환경이 맞으면 반영한다")
    void non_subscription_kept_when_any_purchase_matches() {
        // given: 같은 평생권을 샌드박스로 한 번, 실결제로 한 번 산 유저
        RevenueCatSubscriberBody body = read("""
                {
                  "subscriber": {
                    "entitlements": {
                      "simulation_access": { "expires_date": null, "product_identifier": "twinly_simulation_lifetime" }
                    },
                    "subscriptions": {},
                    "non_subscriptions": {
                      "twinly_simulation_lifetime": [ { "is_sandbox": true }, { "is_sandbox": false } ]
                    }
                  }
                }
                """);

        // when & then: 실결제 구매가 있으므로 prod 에서 인정한다
        assertThat(body.entitlementsIn(RevenueCatEnvironment.PRODUCTION))
                .containsExactly(new RevenueCatEntitlement("simulation_access", null));
    }

    @Test
    @DisplayName("비구독 상품의 구매가 모두 실결제면 stage 에서 버린다")
    void non_subscription_ignored_in_sandbox_when_all_purchases_are_production() {
        // given
        RevenueCatSubscriberBody body = read("""
                {
                  "subscriber": {
                    "entitlements": {
                      "simulation_access": { "expires_date": null, "product_identifier": "twinly_simulation_lifetime" }
                    },
                    "non_subscriptions": {
                      "twinly_simulation_lifetime": [ { "is_sandbox": false } ]
                    }
                  }
                }
                """);

        // when & then
        assertThat(body.entitlementsIn(RevenueCatEnvironment.SANDBOX)).isEmpty();
    }

    @Test
    @DisplayName("권한이 가리키는 상품의 구매 기록이 없으면 결제 환경을 알 수 없으므로 버리지 않는다")
    void unknown_purchase_is_kept() {
        // given: 응답 형식이 바뀌어 구매 기록을 찾지 못하는 상황
        RevenueCatSubscriberBody body = read("""
                {
                  "subscriber": {
                    "entitlements": {
                      "simulation_access": { "expires_date": "2026-10-19T10:13:34Z", "product_identifier": "unknown_product" }
                    }
                  }
                }
                """);

        // when & then: 유료 유저의 권한이 한꺼번에 사라지는 쪽보다 기존 동작을 유지하는 쪽을 택한다
        assertThat(body.entitlementsIn(RevenueCatEnvironment.PRODUCTION))
                .extracting(RevenueCatEntitlement::entitlement)
                .containsExactly("simulation_access");
    }

    @Test
    @DisplayName("구독자 정보나 권한이 비어 있으면 빈 목록을 돌려준다")
    void empty_when_subscriber_missing() {
        // given & when & then
        assertThat(read("{}").entitlementsIn(RevenueCatEnvironment.PRODUCTION)).isEmpty();
        assertThat(read("{\"subscriber\":{}}").entitlementsIn(RevenueCatEnvironment.PRODUCTION)).isEmpty();
    }

    private RevenueCatSubscriberBody read(String json) {
        return JSON_MAPPER.readValue(json, RevenueCatSubscriberBody.class);
    }
}
