package com.nidus.twinly.me.notifier;

import com.nidus.twinly.common.feedback.FeedbackType;
import com.nidus.twinly.common.logging.ErrorLog;
import com.nidus.twinly.common.slack.SlackClient;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.me.event.FeedbackSentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeedbackSlackNotifier {

    private static final String EMPTY = "-";
    private static final String OPTION_DELIMITER = ", ";

    private final SlackClient slackClient;

    @Async("slackTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFeedbackSent(FeedbackSentEvent event) {
        String text = """
                %s (feedbackId=%d)
                유저: %d (%s %s)
                선택: %s
                내용: %s""".formatted(
                title(event.type()), event.feedbackId(),
                event.userId(), Objects.toString(event.appPlatform(), EMPTY), Objects.toString(event.appVersion(), EMPTY),
                escape(String.join(OPTION_DELIMITER, event.optionLabels())),
                escape(event.detail()));

        try {
            slackClient.sendFeedbackAlert(text);
        } catch (BusinessException e) {
            ErrorLog.error(log, ErrorCode.SLACK_SEND_FAILED.name(), String.valueOf(event.userId()), e)
                    .addKeyValue("feedbackId", event.feedbackId())
                    .log("피드백 Slack 알림 발송에 실패했습니다.");
        }
    }

    private String title(FeedbackType type) {
        return switch (type) {
            case WITHDRAWAL -> ":wave: *탈퇴 사유*";
            case SUGGESTION -> ":speech_balloon: *건의하기*";
        };
    }

    private String escape(String value) {
        if (value == null || value.isBlank()) {
            return EMPTY;
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
