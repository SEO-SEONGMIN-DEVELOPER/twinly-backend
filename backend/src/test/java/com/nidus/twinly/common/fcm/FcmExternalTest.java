package com.nidus.twinly.common.fcm;

import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("external")
@SpringBootTest(classes = {FcmConfig.class, FcmSender.class})
@EnableConfigurationProperties(FcmProperties.class)
class FcmExternalTest {

    @Autowired
    FcmSender fcmSender;

    // 죽은 토큰 폐기는 DB 관심사라 대체한다. FCM 연동 검증과 무관하다.
    @MockitoBean
    DeviceTokenRevoker deviceTokenRevoker;

    // 실제 기기 토큰은 .env 로만 받는다. 없으면 발송 테스트를 건너뛴다.
    @Value("${EXTERNAL_TEST_FCM_TOKEN:}")
    String deviceToken;

    @Test
    @DisplayName("가짜 토큰을 보내면 인증은 통과하고 해당 토큰만 실패로 집계된다")
    void invalid_token_is_counted_as_failure() {
        // given: 존재할 수 없는 토큰. 유효하지 않으므로 어떤 기기에도 도달하지 않는다.
        PushMessage pushMessage = pushMessage("external-test-invalid-token");

        // when: 운영 코드인 FcmSender를 그대로 통과시킨다
        PushSendResult result = fcmSender.send(List.of(pushMessage));

        // then: authFailed가 false라는 것은 서비스 계정 인증이 통과했다는 뜻이다.
        //       인증이 깨졌다면 건별 실패가 UNAUTHENTICATED로 잡혀 true가 된다.
        System.out.println("\n===== FcmSender 결과 =====\n" + result + "\n");

        assertThat(result.authFailed()).isFalse();
        assertThat(result.succeeded()).isZero();
        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    @DisplayName("실제 기기 토큰으로 보내면 푸시가 발송된다")
    void send_to_real_device() {
        // given: 기기 토큰이 없으면 발송하지 않고 건너뛴다 (남의 기기로 나가는 사고 방지)
        assumeTrue(!deviceToken.isBlank(), "EXTERNAL_TEST_FCM_TOKEN 미설정 — 실제 발송을 건너뛴다");

        // when: 실제 기기로 발송한다
        PushSendResult result = fcmSender.send(List.of(pushMessage(deviceToken)));

        // then: 토큰이 살아 있고 프로젝트가 일치하면 성공으로 집계된다
        System.out.println("\n===== FcmSender 실제 발송 =====\n" + result + "\n");

        assertThat(result.succeeded()).isEqualTo(1);
    }

    private PushMessage pushMessage(String token) {
        return new PushMessage(1L, PushType.CHAT_MESSAGE, token, Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle("twinly")
                        .setBody("external test")
                        .build())
                .build());
    }
}
