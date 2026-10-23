package com.nidus.twinly.schoolverification.notifier;

import com.nidus.twinly.common.logging.ErrorLog;
import com.nidus.twinly.common.slack.SlackClient;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.schoolverification.event.StudentIdReviewRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class StudentIdSlackNotifier {

    private final SlackClient slackClient;

    @Async("slackTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStudentIdReviewRequested(StudentIdReviewRequestedEvent event) {
        String text = """
                :identification_card: *학생증 검수 요청* (%s)
                회원: %d
                학교: %s
                제출 ID: %s
                어드민 콘솔 학교 인증 관리에서 24시간 안에 처리해 주세요.""".formatted(
                event.resubmitted() ? "재제출" : "첫 제출",
                event.userId(),
                event.organization(),
                event.submissionId());

        try {
            slackClient.sendStudentIdAlert(text);
        } catch (BusinessException e) {
            ErrorLog.error(log, ErrorCode.SLACK_SEND_FAILED.name(), String.valueOf(event.userId()), e)
                    .addKeyValue("submissionId", event.submissionId())
                    .log("학생증 검수 요청 Slack 알림 발송에 실패했습니다.");
        }
    }
}
