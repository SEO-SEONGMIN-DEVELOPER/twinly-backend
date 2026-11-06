package com.nidus.twinly.organization.notifier;

import com.nidus.twinly.common.logging.ErrorLog;
import com.nidus.twinly.common.slack.SlackClient;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.organization.event.CompanyRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompanySlackNotifier {

    private final SlackClient slackClient;

    @Async("slackTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCompanyRegistered(CompanyRegisteredEvent event) {
        String text = """
                :office: *새 회사 등록*
                회사명: %s
                도메인: %s
                기관 ID: %d
                사칭이 의심되면 회사명을 확인해 주세요.""".formatted(
                event.name(),
                event.domain(),
                event.organizationId());

        try {
            slackClient.sendCompanyRegisteredAlert(text);
        } catch (BusinessException e) {
            ErrorLog.error(log, ErrorCode.SLACK_SEND_FAILED.name(), String.valueOf(event.organizationId()), e)
                    .addKeyValue("domain", event.domain())
                    .log("새 회사 등록 Slack 알림 발송에 실패했습니다.");
        }
    }
}
