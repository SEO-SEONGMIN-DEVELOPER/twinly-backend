package com.nidus.twinly.twinupdate.service;

import com.nidus.twinly.aichat.repository.TwinUpdateAiChatRepository;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.survey.SurveyAnswerInput;
import com.nidus.twinly.common.survey.SurveyLoader;
import com.nidus.twinly.common.survey.SurveyOptionName;
import com.nidus.twinly.common.survey.SurveyQuestion;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.me.dto.command.MeCompleteTwinUpdateCommand;
import com.nidus.twinly.me.dto.command.MeSaveTwinUpdateInterestsCommand;
import com.nidus.twinly.me.dto.command.MeStartTwinUpdateCommand;
import com.nidus.twinly.me.dto.command.MeSubmitTwinUpdateSurveyAnswerCommand;
import com.nidus.twinly.me.dto.result.MeCompleteTwinUpdateResult;
import com.nidus.twinly.me.dto.result.MeTwinUpdateResult;
import com.nidus.twinly.twinupdate.domain.TwinUpdateStatus;
import com.nidus.twinly.twinupdate.entity.TwinUpdate;
import com.nidus.twinly.twinupdate.entity.TwinUpdateInterest;
import com.nidus.twinly.twinupdate.entity.TwinUpdateSurveyAnswer;
import com.nidus.twinly.twinupdate.event.TwinUpdateCompletedEvent;
import com.nidus.twinly.twinupdate.repository.TwinUpdateInterestRepository;
import com.nidus.twinly.twinupdate.repository.TwinUpdateRepository;
import com.nidus.twinly.twinupdate.repository.TwinUpdateSurveyAnswerRepository;
import com.nidus.twinly.user.entity.PersonaElement;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.entity.UserSurveyAnswer;
import com.nidus.twinly.user.repository.PersonaElementRepository;
import com.nidus.twinly.user.repository.UserRepository;
import com.nidus.twinly.user.repository.UserSurveyAnswerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class TwinUpdateServiceUnitTest {

    private static final Long ME = 1L;
    private static final Long TWIN_UPDATE_ID = 10L;
    private static final Instant NOW = Instant.parse("2026-10-24T01:00:00Z");

    @Mock
    SurveyLoader surveyLoader;

    @Mock
    TwinUpdateReader twinUpdateReader;

    @Mock
    UserRepository userRepository;

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

    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    TwinUpdateService twinUpdateService;

    // ---------------------------------------------------------------- 시작

    @Test
    @DisplayName("시작: 트윈이 미완성이면 TWIN_NOT_COMPLETED 예외가 발생하고 회차를 만들지 않는다")
    void start_incomplete_twin_throws() {
        // given: 잠금 조회한 회원의 트윈이 미완성
        User user = mock(User.class);
        given(userRepository.findWithLockById(ME)).willReturn(Optional.of(user));
        given(twinUpdateReader.isTwinCompleted(user)).willReturn(false);

        // when & then: TWIN_NOT_COMPLETED + 저장 안 함
        assertThatThrownBy(() -> twinUpdateService.start(ME, new MeStartTwinUpdateCommand(false)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TWIN_NOT_COMPLETED);
        then(twinUpdateRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("시작: 진행 회차가 없고 다음 가능 시각이 남아 있으면 TWIN_UPDATE_NOT_AVAILABLE 예외가 발생한다")
    void start_within_interval_throws() {
        // given: 완성된 트윈, 진행 회차 없음, 다음 가능 시각이 미래
        User user = givenCompletedTwin();
        given(twinUpdateRepository.findWithLockByUserIdAndStatus(ME, TwinUpdateStatus.IN_PROGRESS)).willReturn(Optional.empty());
        given(twinUpdateReader.nextUpdatableAt(user)).willReturn(NOW.plus(Duration.ofDays(3)));

        // when & then: TWIN_UPDATE_NOT_AVAILABLE + 저장 안 함
        assertThatThrownBy(() -> twinUpdateService.start(ME, new MeStartTwinUpdateCommand(false)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TWIN_UPDATE_NOT_AVAILABLE);
        then(twinUpdateRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("시작: restart:false 이고 진행 회차가 있으면 2주 판정 없이 그 회차를 그대로 돌려준다")
    void start_resumes_in_progress() {
        // given: 진행 회차가 있다
        User user = givenCompletedTwin();
        TwinUpdate inProgress = twinUpdate(TWIN_UPDATE_ID);
        given(twinUpdateRepository.findWithLockByUserIdAndStatus(ME, TwinUpdateStatus.IN_PROGRESS)).willReturn(Optional.of(inProgress));

        // when: 이어 하기 요청
        MeTwinUpdateResult result = twinUpdateService.start(ME, new MeStartTwinUpdateCommand(false));

        // then: 같은 회차 + 새로 만들거나 2주 판정을 하지 않음
        assertThat(result.updateId()).isEqualTo(inProgress.getUpdateId());
        assertThat(result.status()).isEqualTo(TwinUpdateStatus.IN_PROGRESS);
        then(twinUpdateReader).should(never()).nextUpdatableAt(user);
        then(twinUpdateRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("시작: restart:true 면 진행 회차를 폐기하고 입력을 지운 뒤, 2주 판정 없이 새 회차를 연다")
    void start_restart_discards_and_creates() {
        // given: 진행 회차가 있고 새 회차 저장은 그대로 돌려준다
        User user = givenCompletedTwin();
        TwinUpdate inProgress = twinUpdate(TWIN_UPDATE_ID);
        given(twinUpdateRepository.findWithLockByUserIdAndStatus(ME, TwinUpdateStatus.IN_PROGRESS)).willReturn(Optional.of(inProgress));
        given(twinUpdateRepository.save(any(TwinUpdate.class))).willAnswer(invocation -> {
            TwinUpdate saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 11L);
            return saved;
        });

        // when: 다시 시작
        MeTwinUpdateResult result = twinUpdateService.start(ME, new MeStartTwinUpdateCommand(true));

        // then: 옛 회차 폐기·플러시·입력 삭제, 새 회차는 다른 updateId 로 진행 중
        assertThat(inProgress.getStatus()).isEqualTo(TwinUpdateStatus.DISCARDED);
        then(twinUpdateRepository).should().saveAndFlush(inProgress);
        then(twinUpdateSurveyAnswerRepository).should().deleteAllByTwinUpdateId(TWIN_UPDATE_ID);
        then(twinUpdateInterestRepository).should().deleteAllByTwinUpdateId(TWIN_UPDATE_ID);
        then(twinUpdateAiChatRepository).should().deleteAllByTwinUpdateId(TWIN_UPDATE_ID);
        assertThat(result.updateId()).isNotEqualTo(inProgress.getUpdateId());
        assertThat(result.status()).isEqualTo(TwinUpdateStatus.IN_PROGRESS);
        then(twinUpdateReader).should(never()).nextUpdatableAt(user);
    }

    @Test
    @DisplayName("시작: 진행 회차가 없고 지금 가능하면 회원 소유의 새 진행 회차를 만든다")
    void start_creates_new() {
        // given: 진행 회차 없음, 다음 가능 시각 없음(지금 가능)
        givenCompletedTwin();
        given(twinUpdateRepository.findWithLockByUserIdAndStatus(ME, TwinUpdateStatus.IN_PROGRESS)).willReturn(Optional.empty());
        given(twinUpdateRepository.save(any(TwinUpdate.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when: 새로 시작
        MeTwinUpdateResult result = twinUpdateService.start(ME, new MeStartTwinUpdateCommand(false));

        // then: 회원 소유 진행 회차 저장 + 설문·관심사 미완료로 응답
        ArgumentCaptor<TwinUpdate> captor = ArgumentCaptor.forClass(TwinUpdate.class);
        then(twinUpdateRepository).should().save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(ME);
        assertThat(captor.getValue().getStatus()).isEqualTo(TwinUpdateStatus.IN_PROGRESS);
        assertThat(result.isSurveyCompleted()).isFalse();
        assertThat(result.isInterestsCompleted()).isFalse();
        assertThat(result.completedAt()).isNull();
        assertThat(result.nextUpdatableAt()).isNull();
    }

    // ---------------------------------------------------------------- 조회

    @Test
    @DisplayName("조회: 완료 회차는 완료 시각과 완료 시각 + 14일을 함께 돌려준다")
    void twinUpdate_completed_returns_times() {
        // given: 완료 회차, 설문·관심사 모두 저장됨
        TwinUpdate completed = twinUpdate(TWIN_UPDATE_ID);
        completed.complete(NOW);
        given(twinUpdateReader.findOwned(ME, completed.getUpdateId())).willReturn(completed);
        given(twinUpdateReader.isSurveyCompleted(anyList())).willReturn(true);
        given(twinUpdateInterestRepository.existsByTwinUpdateId(TWIN_UPDATE_ID)).willReturn(true);

        // when: 회차 조회
        MeTwinUpdateResult result = twinUpdateService.twinUpdate(ME, completed.getUpdateId());

        // then: completed + 두 시각
        assertThat(result).isEqualTo(new MeTwinUpdateResult(
                completed.getUpdateId(), TwinUpdateStatus.COMPLETED, true, true, NOW, NOW.plus(Duration.ofDays(14))));
    }

    // ---------------------------------------------------------------- 설문

    @Test
    @DisplayName("설문: 없는 문항이면 SURVEY_QUESTION_NOT_FOUND 예외가 발생하고 저장하지 않는다")
    void submitSurveyAnswer_unknown_question_throws() {
        // given: 진행 회차, 문항 99 는 없다
        TwinUpdate inProgress = twinUpdate(TWIN_UPDATE_ID);
        given(twinUpdateReader.lockInProgress(ME, inProgress.getUpdateId())).willReturn(inProgress);
        given(surveyLoader.getQuestion(99)).willReturn(null);

        // when & then: SURVEY_QUESTION_NOT_FOUND + upsert 안 함
        assertThatThrownBy(() -> twinUpdateService.submitSurveyAnswer(ME,
                new MeSubmitTwinUpdateSurveyAnswerCommand(inProgress.getUpdateId(), new SurveyAnswerInput(99, SurveyOptionName.A))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SURVEY_QUESTION_NOT_FOUND);
        then(twinUpdateSurveyAnswerRepository).should(never()).upsert(anyLong(), any(), anyString());
    }

    @Test
    @DisplayName("설문: 마지막 문항인데 앞 문항이 빠졌으면 SURVEY_ANSWERS_INCOMPLETE 예외가 발생한다")
    void submitSurveyAnswer_last_question_incomplete_throws() {
        // given: 진행 회차, 문항 3 이 마지막이고 회차 답이 다 차지 않았다
        TwinUpdate inProgress = twinUpdate(TWIN_UPDATE_ID);
        given(twinUpdateReader.lockInProgress(ME, inProgress.getUpdateId())).willReturn(inProgress);
        given(surveyLoader.getQuestion(3)).willReturn(mock(SurveyQuestion.class));
        given(surveyLoader.isLastQuestion(3)).willReturn(true);
        given(twinUpdateReader.answersToCurrentQuestions(TWIN_UPDATE_ID)).willReturn(List.of());
        given(twinUpdateReader.isSurveyCompleted(List.of())).willReturn(false);

        // when & then: SURVEY_ANSWERS_INCOMPLETE
        assertThatThrownBy(() -> twinUpdateService.submitSurveyAnswer(ME,
                new MeSubmitTwinUpdateSurveyAnswerCommand(inProgress.getUpdateId(), new SurveyAnswerInput(3, SurveyOptionName.B))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SURVEY_ANSWERS_INCOMPLETE);
    }

    @Test
    @DisplayName("설문: 중간 문항이면 회차 id·문항·선택지로 upsert 하고 완료 검사는 하지 않는다")
    void submitSurveyAnswer_middle_question_upserts() {
        // given: 진행 회차, 문항 1 은 마지막이 아니다
        TwinUpdate inProgress = twinUpdate(TWIN_UPDATE_ID);
        given(twinUpdateReader.lockInProgress(ME, inProgress.getUpdateId())).willReturn(inProgress);
        given(surveyLoader.getQuestion(1)).willReturn(mock(SurveyQuestion.class));
        given(surveyLoader.isLastQuestion(1)).willReturn(false);

        // when: 중간 문항 제출
        twinUpdateService.submitSurveyAnswer(ME,
                new MeSubmitTwinUpdateSurveyAnswerCommand(inProgress.getUpdateId(), new SurveyAnswerInput(1, SurveyOptionName.A)));

        // then: 회차 답 upsert + 완료 검사 없음
        then(twinUpdateSurveyAnswerRepository).should().upsert(TWIN_UPDATE_ID, 1, "A");
        then(twinUpdateReader).should(never()).answersToCurrentQuestions(anyLong());
    }

    // ---------------------------------------------------------------- 관심사

    @Test
    @DisplayName("관심사: 이번 회차 목록을 지우고 요청 목록 순서대로 다시 저장한다")
    void saveInterests_replaces() {
        // given: 진행 회차
        TwinUpdate inProgress = twinUpdate(TWIN_UPDATE_ID);
        given(twinUpdateReader.lockInProgress(ME, inProgress.getUpdateId())).willReturn(inProgress);

        // when: 관심사 두 개 저장
        twinUpdateService.saveInterests(ME, new MeSaveTwinUpdateInterestsCommand(inProgress.getUpdateId(), List.of("등산", "요리")));

        // then: 회차 관심사 삭제 후 순서대로 저장
        then(twinUpdateInterestRepository).should().deleteAllByTwinUpdateId(TWIN_UPDATE_ID);
        ArgumentCaptor<TwinUpdateInterest> captor = ArgumentCaptor.forClass(TwinUpdateInterest.class);
        then(twinUpdateInterestRepository).should(times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(TwinUpdateInterest::getTwinUpdateId, TwinUpdateInterest::getInterest)
                .containsExactly(
                        tuple(TWIN_UPDATE_ID, "등산"),
                        tuple(TWIN_UPDATE_ID, "요리"));
    }

    // ---------------------------------------------------------------- 완료

    @Test
    @DisplayName("완료: 이미 완료한 회차면 데이터를 다시 반영하지 않고 원래 완료 시각의 결과를 돌려준다")
    void complete_already_completed_returns_original() {
        // given: 완료 회차
        TwinUpdate completed = twinUpdate(TWIN_UPDATE_ID);
        completed.complete(NOW);
        given(twinUpdateReader.lockOwned(ME, completed.getUpdateId())).willReturn(completed);

        // when: 완료 재요청
        MeCompleteTwinUpdateResult result = twinUpdateService.complete(ME, new MeCompleteTwinUpdateCommand(completed.getUpdateId()));

        // then: 원래 결과 + 반영·이벤트 없음
        assertThat(result).isEqualTo(new MeCompleteTwinUpdateResult(completed.getUpdateId(), NOW, NOW.plus(Duration.ofDays(14))));
        then(userSurveyAnswerRepository).shouldHaveNoInteractions();
        then(personaElementRepository).shouldHaveNoInteractions();
        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("완료: 폐기된 회차면 TWIN_UPDATE_STALE 예외가 발생한다")
    void complete_discarded_throws() {
        // given: 폐기 회차
        TwinUpdate discarded = twinUpdate(TWIN_UPDATE_ID);
        discarded.discard();
        given(twinUpdateReader.lockOwned(ME, discarded.getUpdateId())).willReturn(discarded);

        // when & then: TWIN_UPDATE_STALE + 반영 없음
        assertThatThrownBy(() -> twinUpdateService.complete(ME, new MeCompleteTwinUpdateCommand(discarded.getUpdateId())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TWIN_UPDATE_STALE);
        then(personaElementRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("완료: 설문이 다 차지 않았으면 SURVEY_ANSWERS_INCOMPLETE 예외가 발생하고 회차는 진행 중으로 남는다")
    void complete_incomplete_survey_throws() {
        // given: 진행 회차, 설문 미완료
        TwinUpdate inProgress = twinUpdate(TWIN_UPDATE_ID);
        given(twinUpdateReader.lockOwned(ME, inProgress.getUpdateId())).willReturn(inProgress);
        given(twinUpdateReader.answersToCurrentQuestions(TWIN_UPDATE_ID)).willReturn(List.of());
        given(twinUpdateReader.isSurveyCompleted(List.of())).willReturn(false);

        // when & then: SURVEY_ANSWERS_INCOMPLETE + 진행 중 유지
        assertThatThrownBy(() -> twinUpdateService.complete(ME, new MeCompleteTwinUpdateCommand(inProgress.getUpdateId())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SURVEY_ANSWERS_INCOMPLETE);
        assertThat(inProgress.getStatus()).isEqualTo(TwinUpdateStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("완료: 관심사를 저장하지 않았으면 INTERESTS_NOT_SAVED 예외가 발생하고 회차는 진행 중으로 남는다")
    void complete_without_interests_throws() {
        // given: 진행 회차, 설문 완료, 관심사 없음
        TwinUpdate inProgress = twinUpdate(TWIN_UPDATE_ID);
        given(twinUpdateReader.lockOwned(ME, inProgress.getUpdateId())).willReturn(inProgress);
        given(twinUpdateReader.answersToCurrentQuestions(TWIN_UPDATE_ID)).willReturn(List.of());
        given(twinUpdateReader.isSurveyCompleted(List.of())).willReturn(true);
        given(twinUpdateReader.interests(TWIN_UPDATE_ID)).willReturn(List.of());

        // when & then: INTERESTS_NOT_SAVED + 진행 중 유지
        assertThatThrownBy(() -> twinUpdateService.complete(ME, new MeCompleteTwinUpdateCommand(inProgress.getUpdateId())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INTERESTS_NOT_SAVED);
        assertThat(inProgress.getStatus()).isEqualTo(TwinUpdateStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("완료: 회차를 완료로 기록한 뒤 설문 답·설문 요소·관심사를 교체하고 DETAIL 을 더하며 요약 재생성 이벤트를 낸다")
    void complete_applies_update() {
        // given: 설문 1문항(B)·관심사 2개·인터뷰 문답 1건이 있는 진행 회차
        TwinUpdate inProgress = twinUpdate(TWIN_UPDATE_ID);
        TwinUpdateSurveyAnswer answer = surveyAnswer(1, SurveyOptionName.B);
        PersonaElement opennessElement = PersonaElement.create(ME, PersonaDimension.OPENNESS, "새로운 걸 좋아함", NOW);
        given(twinUpdateReader.lockOwned(ME, inProgress.getUpdateId())).willReturn(inProgress);
        given(twinUpdateReader.answersToCurrentQuestions(TWIN_UPDATE_ID)).willReturn(List.of(answer));
        given(twinUpdateReader.isSurveyCompleted(List.of(answer))).willReturn(true);
        given(twinUpdateReader.interests(TWIN_UPDATE_ID)).willReturn(List.of("등산", "요리"));
        given(twinUpdateReader.details(TWIN_UPDATE_ID)).willReturn(List.of("요리는 주로 뭘 해?: 파스타"));
        given(twinUpdateReader.surveyElements(eq(ME), eq(List.of(answer)), any())).willReturn(List.of(opennessElement));

        // when: 완료
        MeCompleteTwinUpdateResult result = twinUpdateService.complete(ME, new MeCompleteTwinUpdateCommand(inProgress.getUpdateId()));

        // then: 회차 완료 기록 + 결과의 다음 가능 시각은 완료 시각 + 14일
        assertThat(inProgress.getStatus()).isEqualTo(TwinUpdateStatus.COMPLETED);
        then(twinUpdateRepository).should().saveAndFlush(inProgress);
        assertThat(result.updateId()).isEqualTo(inProgress.getUpdateId());
        assertThat(result.completedAt()).isEqualTo(inProgress.getCompletedAt());
        assertThat(result.nextUpdatableAt()).isEqualTo(inProgress.getCompletedAt().plus(Duration.ofDays(14)));

        // then: 설문 답 전체 교체
        then(userSurveyAnswerRepository).should().deleteAllByUserId(ME);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserSurveyAnswer>> answersCaptor = ArgumentCaptor.forClass(List.class);
        then(userSurveyAnswerRepository).should().saveAll(answersCaptor.capture());
        assertThat(answersCaptor.getValue())
                .extracting(UserSurveyAnswer::getUserId, UserSurveyAnswer::getQuestionId, UserSurveyAnswer::getOptionName)
                .containsExactly(tuple(ME, 1, SurveyOptionName.B));

        // then: 설문 차원 요소 교체 + 관심사 교체
        then(personaElementRepository).should().deleteByUserIdAndDimensionIn(eq(ME), eq(Set.of(PersonaDimension.OPENNESS)));
        then(personaElementRepository).should().saveAll(List.of(opennessElement));
        then(personaElementRepository).should().deleteByUserIdAndDimension(ME, PersonaDimension.INTEREST);
        then(personaElementRepository).should(never()).deleteByUserIdAndDimension(ME, PersonaDimension.DETAIL);

        // then: 관심사 2개와 DETAIL 1개 저장
        ArgumentCaptor<PersonaElement> elementCaptor = ArgumentCaptor.forClass(PersonaElement.class);
        then(personaElementRepository).should(times(3)).save(elementCaptor.capture());
        assertThat(elementCaptor.getAllValues())
                .extracting(PersonaElement::getDimension, PersonaElement::getExplanation)
                .containsExactly(
                        tuple(PersonaDimension.INTEREST, "등산"),
                        tuple(PersonaDimension.INTEREST, "요리"),
                        tuple(PersonaDimension.DETAIL, "요리는 주로 뭘 해?: 파스타"));

        // then: 요약 재생성 이벤트
        then(eventPublisher).should().publishEvent(new TwinUpdateCompletedEvent(ME));
    }

    // ---------------------------------------------------------------- 진행 회차 폐기

    @Test
    @DisplayName("진행 회차 폐기: 진행 회차가 없으면 아무것도 지우지 않는다")
    void discardInProgress_without_in_progress_is_noop() {
        // given: 진행 회차 없음
        given(twinUpdateRepository.findWithLockByUserIdAndStatus(ME, TwinUpdateStatus.IN_PROGRESS)).willReturn(Optional.empty());

        // when: 폐기
        twinUpdateService.discardInProgress(ME);

        // then: 삭제 없음
        then(twinUpdateSurveyAnswerRepository).shouldHaveNoInteractions();
        then(twinUpdateInterestRepository).shouldHaveNoInteractions();
        then(twinUpdateAiChatRepository).shouldHaveNoInteractions();
    }

    private User givenCompletedTwin() {
        User user = mock(User.class);
        given(userRepository.findWithLockById(ME)).willReturn(Optional.of(user));
        given(twinUpdateReader.isTwinCompleted(user)).willReturn(true);
        return user;
    }

    private TwinUpdate twinUpdate(Long id) {
        TwinUpdate twinUpdate = TwinUpdate.create(ME);
        ReflectionTestUtils.setField(twinUpdate, "id", id);
        return twinUpdate;
    }

    private TwinUpdateSurveyAnswer surveyAnswer(Integer questionId, SurveyOptionName optionName) {
        TwinUpdateSurveyAnswer answer = BeanUtils.instantiateClass(TwinUpdateSurveyAnswer.class);
        ReflectionTestUtils.setField(answer, "twinUpdateId", TWIN_UPDATE_ID);
        ReflectionTestUtils.setField(answer, "questionId", questionId);
        ReflectionTestUtils.setField(answer, "optionName", optionName);
        return answer;
    }
}
