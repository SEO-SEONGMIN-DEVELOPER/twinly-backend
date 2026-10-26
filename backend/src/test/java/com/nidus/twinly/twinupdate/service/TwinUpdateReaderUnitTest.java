package com.nidus.twinly.twinupdate.service;

import com.nidus.twinly.aichat.domain.AiChatSender;
import com.nidus.twinly.aichat.entity.TwinUpdateAiChat;
import com.nidus.twinly.aichat.repository.TwinUpdateAiChatRepository;
import com.nidus.twinly.common.survey.SurveyLoader;
import com.nidus.twinly.common.survey.SurveyOptionName;
import com.nidus.twinly.common.survey.SurveyQuestion;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.twinupdate.domain.TwinUpdateStatus;
import com.nidus.twinly.twinupdate.entity.TwinUpdate;
import com.nidus.twinly.twinupdate.repository.TwinUpdateInterestRepository;
import com.nidus.twinly.twinupdate.repository.TwinUpdateRepository;
import com.nidus.twinly.twinupdate.repository.TwinUpdateSurveyAnswerRepository;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.entity.UserSurveyAnswer;
import com.nidus.twinly.user.repository.PersonaElementRepository;
import com.nidus.twinly.user.repository.UserSurveyAnswerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class TwinUpdateReaderUnitTest {

    private static final Long ME = 1L;
    private static final Long OTHER = 2L;
    private static final Long TWIN_UPDATE_ID = 10L;

    @Mock
    SurveyLoader surveyLoader;

    @Mock
    TwinUpdateRepository twinUpdateRepository;

    @Mock
    TwinUpdateSurveyAnswerRepository twinUpdateSurveyAnswerRepository;

    @Mock
    TwinUpdateInterestRepository twinUpdateInterestRepository;

    @Mock
    TwinUpdateAiChatRepository twinUpdateAiChatRepository;

    @Mock
    UserSurveyAnswerRepository userSurveyAnswerRepository;

    @Mock
    PersonaElementRepository personaElementRepository;

    @InjectMocks
    TwinUpdateReader twinUpdateReader;

    @Test
    @DisplayName("다른 회원의 회차는 존재해도 TWIN_UPDATE_NOT_FOUND 로 숨긴다")
    void lockOwned_other_users_update_throws() {
        // given: 다른 회원 소유 회차
        TwinUpdate others = TwinUpdate.create(OTHER);
        given(twinUpdateRepository.findWithLockByUpdateId(others.getUpdateId())).willReturn(Optional.of(others));

        // when & then: TWIN_UPDATE_NOT_FOUND
        assertThatThrownBy(() -> twinUpdateReader.lockOwned(ME, others.getUpdateId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TWIN_UPDATE_NOT_FOUND);
    }

    @Test
    @DisplayName("진행 회차 잠금: 완료 회차는 TWIN_UPDATE_ALREADY_COMPLETED, 폐기 회차는 TWIN_UPDATE_STALE 이다")
    void lockInProgress_rejects_finished_updates() {
        // given: 내 완료 회차와 폐기 회차
        TwinUpdate completed = TwinUpdate.create(ME);
        completed.complete(Instant.now());
        TwinUpdate discarded = TwinUpdate.create(ME);
        discarded.discard();
        given(twinUpdateRepository.findWithLockByUpdateId(completed.getUpdateId())).willReturn(Optional.of(completed));
        given(twinUpdateRepository.findWithLockByUpdateId(discarded.getUpdateId())).willReturn(Optional.of(discarded));

        // when & then: 상태별 오류 코드
        assertThatThrownBy(() -> twinUpdateReader.lockInProgress(ME, completed.getUpdateId()))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TWIN_UPDATE_ALREADY_COMPLETED);
        assertThatThrownBy(() -> twinUpdateReader.lockInProgress(ME, discarded.getUpdateId()))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TWIN_UPDATE_STALE);
    }

    @Test
    @DisplayName("트윈 완성 판정: 현재 설문에 없는 문항의 답은 세지 않아 /me/status 와 같은 기준으로 미완성이다")
    void isTwinCompleted_ignores_unknown_questions() {
        // given: 문항 2개 설문, 답 2개 중 하나는 없어진 문항, 관심사·AI 대화 완료
        User user = mock(User.class);
        given(user.getId()).willReturn(ME);
        given(userSurveyAnswerRepository.findAllByUserId(ME)).willReturn(List.of(
                UserSurveyAnswer.create(ME, 1, SurveyOptionName.A),
                UserSurveyAnswer.create(ME, 99, SurveyOptionName.A)));
        given(surveyLoader.getQuestion(1)).willReturn(mock(SurveyQuestion.class));
        given(surveyLoader.getQuestion(99)).willReturn(null);
        given(surveyLoader.getAllQuestions()).willReturn(List.of(mock(SurveyQuestion.class), mock(SurveyQuestion.class)));

        // when: 완성 판정
        boolean completed = twinUpdateReader.isTwinCompleted(user);

        // then: 미완성
        assertThat(completed).isFalse();
    }

    @Test
    @DisplayName("다음 가능 시각: 완성 기록이 없으면 null(지금 가능)이다")
    void nextUpdatableAt_without_completion_is_null() {
        // given: AI 대화 완료 시각·완료 회차 모두 없음
        User user = mock(User.class);
        given(user.getId()).willReturn(ME);
        given(twinUpdateRepository.findFirstByUserIdAndStatusOrderByCompletedAtDesc(ME, TwinUpdateStatus.COMPLETED)).willReturn(Optional.empty());

        // when & then
        assertThat(twinUpdateReader.nextUpdatableAt(user)).isNull();
    }

    @Test
    @DisplayName("다음 가능 시각: 처음 완성 시각과 마지막 업데이트 완료 시각 중 늦은 쪽 + 14일이다")
    void nextUpdatableAt_uses_latest_completion() {
        // given: 처음 완성은 3일 전, 마지막 업데이트는 하루 전
        Instant createdAt = Instant.now().minus(Duration.ofDays(3)).truncatedTo(ChronoUnit.MICROS);
        Instant updatedAt = Instant.now().minus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MICROS);
        User user = mock(User.class);
        given(user.getId()).willReturn(ME);
        given(user.getAiChatCompletedAt()).willReturn(createdAt);
        TwinUpdate lastUpdate = TwinUpdate.create(ME);
        lastUpdate.complete(updatedAt);
        given(twinUpdateRepository.findFirstByUserIdAndStatusOrderByCompletedAtDesc(ME, TwinUpdateStatus.COMPLETED)).willReturn(Optional.of(lastUpdate));

        // when & then: 하루 전 + 14일
        assertThat(twinUpdateReader.nextUpdatableAt(user)).isEqualTo(updatedAt.plus(Duration.ofDays(14)));
    }

    @Test
    @DisplayName("다음 가능 시각: 관리자 초기화 뒤 다시 만든 시각이 옛 업데이트보다 늦으면 그 시각을 기준으로 한다")
    void nextUpdatableAt_prefers_recreated_twin() {
        // given: 옛 업데이트는 20일 전, 다시 만든 트윈은 2일 전
        Instant recreatedAt = Instant.now().minus(Duration.ofDays(2)).truncatedTo(ChronoUnit.MICROS);
        User user = mock(User.class);
        given(user.getId()).willReturn(ME);
        given(user.getAiChatCompletedAt()).willReturn(recreatedAt);
        TwinUpdate oldUpdate = TwinUpdate.create(ME);
        oldUpdate.complete(Instant.now().minus(Duration.ofDays(20)));
        given(twinUpdateRepository.findFirstByUserIdAndStatusOrderByCompletedAtDesc(ME, TwinUpdateStatus.COMPLETED)).willReturn(Optional.of(oldUpdate));

        // when & then: 2일 전 + 14일
        assertThat(twinUpdateReader.nextUpdatableAt(user)).isEqualTo(recreatedAt.plus(Duration.ofDays(14)));
    }

    @Test
    @DisplayName("다음 가능 시각: 14일이 지났으면 null(지금 가능)이다")
    void nextUpdatableAt_after_interval_is_null() {
        // given: 30일 전에 완성
        User user = mock(User.class);
        given(user.getId()).willReturn(ME);
        given(user.getAiChatCompletedAt()).willReturn(Instant.now().minus(Duration.ofDays(30)));
        given(twinUpdateRepository.findFirstByUserIdAndStatusOrderByCompletedAtDesc(ME, TwinUpdateStatus.COMPLETED)).willReturn(Optional.empty());

        // when & then
        assertThat(twinUpdateReader.nextUpdatableAt(user)).isNull();
    }

    @Test
    @DisplayName("인터뷰 DETAIL: 같은 턴의 질문·답 쌍만 '질문: 답' 으로 만들고, 답하지 않은 마지막 질문은 뺀다")
    void details_pairs_answered_turns_only() {
        // given: 0턴 문답 + 답 없는 1턴 질문
        given(twinUpdateAiChatRepository.findByTwinUpdateIdOrderByTurnIndexAscSenderDesc(TWIN_UPDATE_ID)).willReturn(List.of(
                chat(AiChatSender.AI, "요리는 주로 뭘 해?", 0),
                chat(AiChatSender.USER, "파스타", 0),
                chat(AiChatSender.AI, "제일 자신 있는 메뉴는?", 1)));

        // when
        List<String> details = twinUpdateReader.details(TWIN_UPDATE_ID);

        // then: 0턴 한 건만
        assertThat(details).containsExactly("요리는 주로 뭘 해?: 파스타");
    }

    private TwinUpdateAiChat chat(AiChatSender sender, String message, int turnIndex) {
        return TwinUpdateAiChat.create(TWIN_UPDATE_ID, sender, message, turnIndex);
    }
}
