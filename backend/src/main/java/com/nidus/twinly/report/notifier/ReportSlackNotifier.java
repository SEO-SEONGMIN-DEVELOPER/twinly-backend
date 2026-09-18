package com.nidus.twinly.report.notifier;

import com.nidus.twinly.common.logging.ErrorLog;
import com.nidus.twinly.common.slack.SlackClient;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.report.event.AiUtteranceReportedEvent;
import com.nidus.twinly.report.event.UserReportedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportSlackNotifier {

    private static final String EMPTY = "-";
    private static final String ELLIPSIS = "…";
    private static final int MAX_TEXT_LENGTH = 300;

    private final SlackClient slackClient;

    @Async("slackTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserReported(UserReportedEvent event) {
        send(event.reporterUserId(), event.reportId(), """
                :rotating_light: *유저 신고* (reportId=%s)
                신고자: %d → 대상: %d
                사유: %s
                상세: %s""".formatted(
                event.reportId(), event.reporterUserId(), event.reportedUserId(), event.reason(), escape(event.detail())));
    }

    @Async("slackTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAiUtteranceReported(AiUtteranceReportedEvent event) {
        send(event.reporterUserId(), event.reportId(), """
                :robot_face: *AI 발화 신고* (reportId=%s)
                신고자: %d → 대상: %d (sceneId=%d)
                사유: %s
                발화: %s""".formatted(
                event.reportId(), event.reporterUserId(), event.reportedUserId(), event.sceneId(),
                escape(event.reason()), escape(event.utteranceText())));
    }

    private void send(Long reporterUserId, Long reportId, String text) {
        try {
            slackClient.sendReportAlert(text);
        } catch (BusinessException e) {
            ErrorLog.error(log, ErrorCode.SLACK_SEND_FAILED.name(), String.valueOf(reporterUserId), e)
                    .addKeyValue("reportId", reportId)
                    .log("신고 Slack 알림 발송에 실패했습니다.");
        }
    }

    private String escape(String value) {
        if (value == null || value.isBlank()) {
            return EMPTY;
        }
        return truncate(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String truncate(String value) {
        if (value.codePointCount(0, value.length()) <= MAX_TEXT_LENGTH) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, MAX_TEXT_LENGTH)) + ELLIPSIS;
    }
}
