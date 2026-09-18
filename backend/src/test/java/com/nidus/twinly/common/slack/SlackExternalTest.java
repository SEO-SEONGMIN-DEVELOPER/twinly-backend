package com.nidus.twinly.common.slack;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("external")
@SpringBootTest(classes = {SlackClient.class, JacksonAutoConfiguration.class})
@EnableConfigurationProperties(SlackProperties.class)
class SlackExternalTest {

    // 존재할 수 없는 워크스페이스·웹훅 식별자라 Slack 이 거부하므로 어느 채널에도 전달되지 않는다.
    private static final String INVALID_WEBHOOK_URL = "https://hooks.slack.com/services/T00000000/B00000000/externaltestinvalid";

    @Autowired
    SlackClient slackClient;

    @Autowired
    SlackProperties slackProperties;

    @Autowired
    JsonMapper jsonMapper;

    @Test
    @DisplayName("실제 Slack 웹훅으로 신고 알림을 보내면 예외 없이 채널에 게시된다")
    void send_report_alert() {
        // given: 운영자가 실제 신고로 오인하지 않도록 테스트임을 드러낸 문구 (수신 채널은 .env 의 웹훅 URL 로만 정해진다)
        String text = "[external-test] SlackClient 연동 검증 메시지입니다. 실제 신고가 아니므로 무시하세요.";

        // when & then: 웹훅 URL·요청 형식이 유효하면 Slack 이 200 으로 받아 예외가 나지 않는다
        assertThatCode(() -> slackClient.sendReportAlert(text))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("존재하지 않는 웹훅으로 보내면 Slack 의 거부 응답을 SLACK_SEND_FAILED 로 감싼다")
    void invalid_webhook_is_wrapped() {
        // given: 형식만 맞고 존재하지 않는 웹훅 URL 로 만든 클라이언트
        SlackProperties invalid = new SlackProperties(INVALID_WEBHOOK_URL, slackProperties.connectTimeout(), slackProperties.readTimeout());
        SlackClient invalidClient = new SlackClient(jsonMapper, invalid);

        // when & then: 4xx 응답이 우리 도메인 예외로 변환되어 올라온다
        assertThatThrownBy(() -> invalidClient.sendReportAlert("[external-test] 전달되면 안 되는 메시지"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SLACK_SEND_FAILED);
    }
}
