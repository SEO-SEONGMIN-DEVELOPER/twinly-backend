package com.nidus.twinly.purchase.client;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.purchase.RevenueCatProperties;
import com.nidus.twinly.purchase.domain.RevenueCatEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("external")
@SpringBootTest(
        classes = {RevenueCatClient.class, JacksonAutoConfiguration.class},
        properties = "revenue-cat.environment=SANDBOX")
@EnableConfigurationProperties(RevenueCatProperties.class)
class RevenueCatExternalTest {

    private static final Logger log = LoggerFactory.getLogger(RevenueCatExternalTest.class);

    // 조회만으로도 구독자가 생성되므로(201) 운영 데이터와 섞이지 않게 접두사로 격리한다.
    private static final String APP_USER_ID = "external-test-revenue-cat-client";

    @Autowired
    RevenueCatClient revenueCatClient;

    @Autowired
    RevenueCatProperties revenueCatProperties;

    @Autowired
    JsonMapper jsonMapper;

    @AfterEach
    void cleanUp() {
        // 테스트가 만든 구독자를 지운다. 삭제 권한이 없을 수 있으므로 실패해도 테스트를 가리지 않고 로그만 남긴다.
        try {
            RestClient.builder()
                    .baseUrl("https://api.revenuecat.com/v1")
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + revenueCatProperties.secretApiKey())
                    .build()
                    .delete()
                    .uri("/subscribers/{appUserId}", APP_USER_ID)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("외부 테스트 구독자 정리 실패 — 대시보드에서 직접 삭제하세요: {}", APP_USER_ID, e);
        }
    }

    @Test
    @DisplayName("실제 RevenueCat 에 구독자를 조회하면 예외 없이 권한 목록이 파싱된다")
    void entitlements_returns_parsed_list() {
        // when: 실제 자격증명으로 구독자 조회
        List<RevenueCatEntitlement> entitlements = revenueCatClient.entitlements(APP_USER_ID);

        // then: 인증·엔드포인트·응답 파싱이 모두 성립한다 (구매가 없으므로 목록 내용은 단언하지 않는다)
        assertThat(entitlements).isNotNull();
    }

    @Test
    @DisplayName("구독자 조회를 두 번 해도 같은 형태로 응답이 파싱된다")
    void entitlements_is_repeatable() {
        // given: 첫 조회로 구독자가 생성된 상태
        revenueCatClient.entitlements(APP_USER_ID);

        // when & then: 같은 식별자로 다시 조회해도 파싱에 실패하지 않는다 (생성 201 / 조회 200 양쪽 경로 확인)
        assertThatCode(() -> revenueCatClient.entitlements(APP_USER_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("자격증명이 잘못되면 RestClient 예외를 REVENUE_CAT_SYNC_FAILED 로 감싼다")
    void invalid_credential_is_wrapped() {
        // given: 형식만 그럴듯한 가짜 키로 만든 클라이언트 (요청이 거부되므로 구독자가 생기지 않는다)
        RevenueCatProperties invalid = new RevenueCatProperties("secret", "sk_invalid_external_test", RevenueCatEnvironment.SANDBOX);
        RevenueCatClient invalidClient = new RevenueCatClient(jsonMapper, invalid);

        // when & then: 401 이 우리 도메인 예외로 변환되어 올라온다
        assertThatThrownBy(() -> invalidClient.entitlements(APP_USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REVENUE_CAT_SYNC_FAILED);
    }
}
