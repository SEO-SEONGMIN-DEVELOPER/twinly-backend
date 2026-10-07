package com.nidus.twinly.me.notifier;

import com.nidus.twinly.app.domain.AppPlatform;
import com.nidus.twinly.common.feedback.FeedbackType;
import com.nidus.twinly.common.slack.SlackClient;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.me.event.FeedbackSentEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class FeedbackSlackNotifierUnitTest {

    @Mock
    SlackClient slackClient;

    @InjectMocks
    FeedbackSlackNotifier feedbackSlackNotifier;

    @Test
    @DisplayName("건의하기는 유저·앱 정보, 고른 선택지, 내용을 담아 피드백 채널로 보낸다")
    void suggestion_contains_options_and_detail() {
        // given: 선택지 두 개와 내용을 담은 건의하기
        FeedbackSentEvent event = new FeedbackSentEvent(10L, 16L, FeedbackType.SUGGESTION,
                List.of("기능 제안", "오류 제보"), "알림이 늦게 와요", AppPlatform.IOS, "1.2.3");

        // when: 알림 발송
        feedbackSlackNotifier.onFeedbackSent(event);

        // then: 종류·유저·앱 정보·선택지·내용이 모두 담긴다
        assertThat(sentText()).isEqualTo("""
                :speech_balloon: *건의하기* (feedbackId=10)
                유저: 16 (IOS 1.2.3)
                선택: 기능 제안, 오류 제보
                내용: 알림이 늦게 와요""");
    }

    @Test
    @DisplayName("탈퇴 사유에 선택지·내용·앱 정보가 없으면 null 대신 - 로 표시한다")
    void empty_withdrawal_is_dash() {
        // given: 아무것도 고르지 않고 앱 헤더도 없는 탈퇴 사유
        FeedbackSentEvent event = new FeedbackSentEvent(10L, 16L, FeedbackType.WITHDRAWAL, List.of(), null, null, null);

        // when: 알림 발송
        feedbackSlackNotifier.onFeedbackSent(event);

        // then: 비어 있는 칸은 - 로 표시되고 null 문자열이 노출되지 않는다
        String text = sentText();
        assertThat(text).startsWith(":wave: *탈퇴 사유*");
        assertThat(text).contains("유저: 16 (- -)", "선택: -", "내용: -");
        assertThat(text).doesNotContain("null");
    }

    @Test
    @DisplayName("내용의 Slack 제어 문자(<, >, &)를 이스케이프해 채널 멘션·링크로 해석되지 않게 한다")
    void escapes_slack_control_characters() {
        // given: 내용에 채널 전체 멘션과 위장 링크를 넣은 건의하기
        FeedbackSentEvent event = new FeedbackSentEvent(10L, 16L, FeedbackType.SUGGESTION,
                List.of(), "<!channel> <https://evil.example|정상링크> & 끝", null, null);

        // when: 알림 발송
        feedbackSlackNotifier.onFeedbackSent(event);

        // then: 원문 기호가 엔티티로 바뀌어 전달된다
        String text = sentText();
        assertThat(text).contains("&lt;!channel&gt; &lt;https://evil.example|정상링크&gt; &amp; 끝");
        assertThat(text).doesNotContain("<!channel>");
    }

    @Test
    @DisplayName("Slack 발송이 실패해도 예외를 밖으로 던지지 않는다")
    void send_failure_is_not_propagated() {
        // given: Slack 이 거부하는 상황
        willThrow(new BusinessException(ErrorCode.SLACK_SEND_FAILED)).given(slackClient).sendFeedbackAlert(anyString());
        FeedbackSentEvent event = new FeedbackSentEvent(10L, 16L, FeedbackType.SUGGESTION, List.of(), "건의", null, null);

        // when & then: 로그만 남기고 조용히 끝난다
        assertThatCode(() -> feedbackSlackNotifier.onFeedbackSent(event))
                .doesNotThrowAnyException();
    }

    private String sentText() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        then(slackClient).should().sendFeedbackAlert(captor.capture());
        return captor.getValue();
    }
}
