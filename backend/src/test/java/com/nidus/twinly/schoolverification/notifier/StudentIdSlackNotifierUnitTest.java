package com.nidus.twinly.schoolverification.notifier;

import com.nidus.twinly.common.slack.SlackClient;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.schoolverification.event.StudentIdReviewRequestedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class StudentIdSlackNotifierUnitTest {

    private static final UUID SUBMISSION_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Mock
    SlackClient slackClient;

    @InjectMocks
    StudentIdSlackNotifier studentIdSlackNotifier;

    @Test
    @DisplayName("가입 후 첫 제출은 회원 ID·학교·제출 ID 를 담아 학생증 채널로 보내고, 성명·사진 같은 개인정보는 싣지 않는다")
    void first_submission_alert() {
        // given: 학생증으로 가입해 검수 대기열에 들어온 제출
        StudentIdReviewRequestedEvent event = new StudentIdReviewRequestedEvent(31L, SUBMISSION_ID, "성균관대학교", false);

        // when: 알림 발송
        studentIdSlackNotifier.onStudentIdReviewRequested(event);

        // then: 학생증 채널로 정해진 형식의 문구가 나간다
        assertThat(sentText()).isEqualTo("""
                :identification_card: *학생증 검수 요청* (첫 제출)
                회원: 31
                학교: 성균관대학교
                제출 ID: 44444444-4444-4444-4444-444444444444
                어드민 콘솔 학교 인증 관리에서 24시간 안에 처리해 주세요.""");
    }

    @Test
    @DisplayName("반려 뒤 다시 낸 제출은 재제출로 표시한다")
    void resubmission_alert() {
        // given: 반려 뒤 재제출
        StudentIdReviewRequestedEvent event = new StudentIdReviewRequestedEvent(31L, SUBMISSION_ID, "성균관대학교", true);

        // when: 알림 발송
        studentIdSlackNotifier.onStudentIdReviewRequested(event);

        // then: 제목에 재제출이 붙는다
        assertThat(sentText()).startsWith(":identification_card: *학생증 검수 요청* (재제출)");
    }

    @Test
    @DisplayName("Slack 발송이 실패해도 예외를 밖으로 던지지 않는다")
    void send_failure_is_not_propagated() {
        // given: Slack 이 거부하는 상황
        willThrow(new BusinessException(ErrorCode.SLACK_SEND_FAILED)).given(slackClient).sendStudentIdAlert(anyString());
        StudentIdReviewRequestedEvent event = new StudentIdReviewRequestedEvent(31L, SUBMISSION_ID, "성균관대학교", false);

        // when & then: 검수 대기열 등록(커밋 뒤)에 영향을 주지 않도록 예외를 삼킨다
        assertThatCode(() -> studentIdSlackNotifier.onStudentIdReviewRequested(event)).doesNotThrowAnyException();
    }

    private String sentText() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        then(slackClient).should().sendStudentIdAlert(captor.capture());
        return captor.getValue();
    }
}
