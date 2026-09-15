package com.nidus.twinly.auth.client;

import com.nidus.twinly.auth.config.NiceProperties;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 로컬 PC IP 는 NICE 에 미등록이라 이 테스트는 그대로 돌리면 1007(허용되지 않은 IP) 로 실패한다.
 * scripts/nice-external-test.sh 로 실행하면 stage EC2 를 거치는 SOCKS 터널을 열어 stage NAT IP 로 나간다.
 */
@Tag("external")
@SpringBootTest(classes = NiceAuthClient.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@EnableConfigurationProperties(NiceProperties.class)
class NiceAuthExternalTest {

    @Autowired
    NiceAuthClient niceAuthClient;

    @Autowired
    NiceProperties niceProperties;

    @Autowired
    JsonMapper jsonMapper;

    @Test
    @DisplayName("실제 자격증명으로 접근 토큰을 발급받으면 토큰과 키 유도 재료(ticket, iterators)가 함께 온다")
    void issueToken_returns_token_with_key_material() {
        // given: .env 의 실제 client_id / client_secret, 이 PC 의 IP 가 NICE 에 등록되어 있어야 한다

        // when: 토큰 발급 (과금·상태 변경 없음)
        NiceTokenBody body = niceAuthClient.issueToken();

        // then: 응답 형식만 확인한다. 값의 내용은 단언하지 않는다
        assertThat(body.resultCode()).isEqualTo("0000");
        assertThat(body.accessToken()).isNotBlank();
        assertThat(body.ticket()).isNotBlank();
        assertThat(body.iterators()).isPositive();
        assertThat(body.expiresIn()).isGreaterThan(System.currentTimeMillis());
    }

    @Test
    @DisplayName("발급받은 토큰으로 인증 URL을 요청하면 auth_url과 transaction_id가 온다")
    void requestAuthUrl_returns_auth_url_and_transaction_id() {
        // given: 실제 토큰. 인증 URL 발급은 사용자가 인증을 완료하기 전까지 과금되지 않는다
        NiceTokenBody token = niceAuthClient.issueToken();
        String requestNo = "TWINLY-EXT-" + UUID.randomUUID();

        // when: 인증 URL 요청. svc_types·return_url·close_url 이 계약·규격에 맞는지 여기서 걸러진다
        NiceAuthUrlBody body = niceAuthClient.requestAuthUrl(token.accessToken(), requestNo);

        // then: 형식만 확인한다
        assertThat(body.resultCode()).isEqualTo("0000");
        assertThat(body.authUrl()).startsWith("https://");
        assertThat(body.transactionId()).isNotBlank();
    }

    @Test
    @DisplayName("잘못된 시크릿으로 요청하면 연동 실패(IDENTITY_VERIFICATION_FAILED)로 감싸 올린다")
    void issueToken_with_invalid_secret_throws() {
        // given: 시크릿만 틀린 클라이언트
        NiceAuthClient invalidClient = new NiceAuthClient(jsonMapper, new NiceProperties(
                niceProperties.clientId(), "invalid-client-secret",
                niceProperties.returnUrl(), niceProperties.closeUrl()));

        // when & then: HTTP 4xx 든 result_code 실패든 같은 에러코드로 나간다
        assertThatThrownBy(invalidClient::issueToken)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDENTITY_VERIFICATION_FAILED);
    }

    @Test
    @DisplayName("유효하지 않은 토큰으로 인증 URL을 요청하면 토큰 거절 예외(NiceTokenRejectedException)로 구분되어 올라간다")
    void requestAuthUrl_with_invalid_token_throws_token_rejected() {
        // given: 형식만 JWT 인 가짜 토큰

        // when & then: 게이트웨이 1003/1004 또는 서비스 3043 계열이 토큰 거절로 매핑되는지 실제 응답으로 확인한다
        assertThatThrownBy(() -> niceAuthClient.requestAuthUrl("invalid.access.token", "TWINLY-EXT-" + UUID.randomUUID()))
                .isInstanceOf(NiceTokenRejectedException.class);
    }
}
