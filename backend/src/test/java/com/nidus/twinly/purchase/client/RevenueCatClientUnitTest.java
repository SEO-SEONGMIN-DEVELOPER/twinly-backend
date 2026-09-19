package com.nidus.twinly.purchase.client;

import com.nidus.twinly.purchase.RevenueCatProperties;
import com.nidus.twinly.purchase.domain.RevenueCatEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RevenueCatClientUnitTest {

    RevenueCatClient revenueCatClient;

    @BeforeEach
    void setUp() {
        RevenueCatProperties properties = new RevenueCatProperties("secret", "sk_test", RevenueCatEnvironment.SANDBOX,
                Duration.ofSeconds(3), Duration.ofSeconds(5), Duration.ofSeconds(30));
        revenueCatClient = new RevenueCatClient(JsonMapper.builder().build(), properties);
    }

    @Test
    @DisplayName("429 응답 코드가 7638 이면 같은 유저에 대한 동시 요청 충돌로 판단한다")
    void concurrent_request_code_is_conflict() {
        // given: RevenueCat 이 같은 작업이 이미 진행 중이라며 거절한 응답
        HttpClientErrorException e = tooManyRequests("{\"code\":7638,\"message\":\"There is another request in flight trying to perform the same action.\"}");

        // when & then
        assertThat(isConcurrentRequest(e)).isTrue();
    }

    @Test
    @DisplayName("429 라도 응답 코드가 7638 이 아니면 원인을 특정할 수 없으므로 충돌로 판단하지 않는다")
    void other_code_is_not_conflict() {
        // given: 7638 이 아닌 임의의 코드를 담은 429
        HttpClientErrorException e = tooManyRequests("{\"code\":1234,\"message\":\"any other reason\"}");

        // when & then
        assertThat(isConcurrentRequest(e)).isFalse();
    }

    @Test
    @DisplayName("429 응답 본문이 JSON 이 아니면 충돌로 판단하지 않는다")
    void non_json_body_is_not_conflict() {
        // given: 프록시 등이 돌려준 텍스트 본문
        HttpClientErrorException e = tooManyRequests("Too Many Requests");

        // when & then
        assertThat(isConcurrentRequest(e)).isFalse();
    }

    private boolean isConcurrentRequest(HttpClientErrorException e) {
        return ReflectionTestUtils.<Boolean>invokeMethod(revenueCatClient, "isConcurrentRequest", e);
    }

    private HttpClientErrorException tooManyRequests(String body) {
        return HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                HttpHeaders.EMPTY, body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }
}
