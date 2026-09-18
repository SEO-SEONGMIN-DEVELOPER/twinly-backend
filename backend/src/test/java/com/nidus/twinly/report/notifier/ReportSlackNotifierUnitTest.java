package com.nidus.twinly.report.notifier;

import com.nidus.twinly.common.slack.SlackClient;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.report.domain.ReportReason;
import com.nidus.twinly.report.event.AiUtteranceReportedEvent;
import com.nidus.twinly.report.event.UserReportedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class ReportSlackNotifierUnitTest {

    @Mock
    SlackClient slackClient;

    @InjectMocks
    ReportSlackNotifier reportSlackNotifier;

    @Test
    @DisplayName("유저 신고 상세의 Slack 제어 문자(<, >, &)를 이스케이프해 채널 멘션·링크로 해석되지 않게 한다")
    void userReported_escapes_slack_control_characters() {
        // given: 상세에 채널 전체 멘션과 위장 링크를 넣은 신고
        UserReportedEvent event = new UserReportedEvent(1L, 16L, 13L, ReportReason.HARASSMENT, "<!channel> <https://evil.example|정상링크> & 끝");

        // when: 알림 발송
        reportSlackNotifier.onUserReported(event);

        // then: 원문 기호가 엔티티로 바뀌어 전달된다
        String text = sentText();
        assertThat(text).contains("&lt;!channel&gt; &lt;https://evil.example|정상링크&gt; &amp; 끝");
        assertThat(text).doesNotContain("<!channel>");
    }

    @Test
    @DisplayName("유저 신고 상세가 비어 있으면 null 대신 - 로 표시한다")
    void userReported_blank_detail_is_dash() {
        // given: 상세 없이 사유만 고른 신고
        UserReportedEvent event = new UserReportedEvent(1L, 16L, 13L, ReportReason.SPAM, null);

        // when: 알림 발송
        reportSlackNotifier.onUserReported(event);

        // then: 상세 줄이 - 로 표시되고 null 문자열이 노출되지 않는다
        String text = sentText();
        assertThat(text).contains("상세: -");
        assertThat(text).doesNotContain("null");
    }

    @Test
    @DisplayName("AI 발화가 300자를 넘으면 300자에서 자르고 … 를 붙인다")
    void aiUtteranceReported_truncates_long_text() {
        // given: 301자짜리 발화
        String utterance = "가".repeat(300) + "나";
        AiUtteranceReportedEvent event = new AiUtteranceReportedEvent(1L, 16L, 13L, 77L, utterance, "HARASSMENT");

        // when: 알림 발송
        reportSlackNotifier.onAiUtteranceReported(event);

        // then: 앞 300자만 남고 말줄임표가 붙는다
        String text = sentText();
        assertThat(text).contains("발화: " + "가".repeat(300) + "…");
        assertThat(text).doesNotContain("나");
    }

    @Test
    @DisplayName("자르는 경계에 이모지가 걸려도 서로게이트 쌍을 쪼개지 않는다")
    void truncate_does_not_split_surrogate_pair() {
        // given: 299자 뒤에 이모지(UTF-16 두 칸)가 오고 뒤에 글자가 더 있는 상세
        String detail = "가".repeat(299) + "😀" + "나";
        UserReportedEvent event = new UserReportedEvent(1L, 16L, 13L, ReportReason.OTHER, detail);

        // when: 알림 발송
        reportSlackNotifier.onUserReported(event);

        // then: 이모지까지 온전히 300자로 남고 깨진 문자가 생기지 않는다
        assertThat(sentText()).contains("가".repeat(299) + "😀" + "…");
    }

    @Test
    @DisplayName("Slack 발송이 실패해도 예외를 밖으로 던지지 않는다")
    void send_failure_is_not_propagated() {
        // given: Slack 이 거부하는 상황
        willThrow(new BusinessException(ErrorCode.SLACK_SEND_FAILED)).given(slackClient).sendReportAlert(anyString());
        UserReportedEvent event = new UserReportedEvent(1L, 16L, 13L, ReportReason.SPAM, "광고");

        // when & then: 로그만 남기고 조용히 끝난다
        assertThatCode(() -> reportSlackNotifier.onUserReported(event))
                .doesNotThrowAnyException();
    }

    private String sentText() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        then(slackClient).should().sendReportAlert(captor.capture());
        return captor.getValue();
    }
}
