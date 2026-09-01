package com.nidus.twinly.auth.client;

import com.nidus.twinly.auth.config.PortOneProperties;
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

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("external")
@SpringBootTest(classes = PortOneIdentityClient.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@EnableConfigurationProperties(PortOneProperties.class)
class PortOneIdentityExternalTest {

    @Autowired
    PortOneIdentityClient portOneIdentityClient;

    @Autowired
    JsonMapper jsonMapper;

    @Test
    @DisplayName("실제 PortOne에 없는 인증 건을 조회하면 예외 없이 빈 결과가 돌아온다")
    void identityVerification_not_found_returns_empty() {
        // given: 실제로 발급된 적 없는 인증 건 id (조회만 하므로 과금·상태 변경이 없다)
        String identityVerificationId = "external-test-" + UUID.randomUUID();

        // when: 실제 자격증명으로 PortOne 조회
        Optional<PortOneIdentityVerificationBody> body =
                portOneIdentityClient.identityVerification(identityVerificationId);

        // then: 시크릿이 유효해야만 401이 아닌 404가 오고, 404는 빈 결과로 매핑된다
        assertThat(body).isEmpty();
    }

    @Test
    @DisplayName("잘못된 시크릿으로 조회하면 인증 실패를 IDENTITY_VERIFICATION_FAILED로 감싸 올린다")
    void identityVerification_with_invalid_secret_throws() {
        // given: 시크릿만 틀린 클라이언트
        PortOneIdentityClient invalidClient = new PortOneIdentityClient(
                jsonMapper, new PortOneProperties("invalid-api-secret", Set.of(PortOneChannelType.LIVE)));

        // when & then: 인증 실패(401)를 "인증되지 않음"이 아니라 연동 실패로 구분해 올린다
        assertThatThrownBy(() -> invalidClient.identityVerification("external-test-" + UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDENTITY_VERIFICATION_FAILED);
    }
}
