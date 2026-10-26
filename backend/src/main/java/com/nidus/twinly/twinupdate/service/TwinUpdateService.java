package com.nidus.twinly.twinupdate.service;

import com.nidus.twinly.aichat.repository.TwinUpdateAiChatRepository;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.survey.SurveyLoader;
import com.nidus.twinly.common.survey.SurveyOptionName;
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
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TwinUpdateService {

    private final SurveyLoader surveyLoader;
    private final TwinUpdateReader twinUpdateReader;

    private final UserRepository userRepository;
    private final TwinUpdateRepository twinUpdateRepository;
    private final TwinUpdateSurveyAnswerRepository twinUpdateSurveyAnswerRepository;
    private final TwinUpdateInterestRepository twinUpdateInterestRepository;
    private final TwinUpdateAiChatRepository twinUpdateAiChatRepository;
    private final UserSurveyAnswerRepository userSurveyAnswerRepository;
    private final PersonaElementRepository personaElementRepository;

    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public MeTwinUpdateResult start(Long userId, MeStartTwinUpdateCommand command) {
        User user = userRepository.findWithLockById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!twinUpdateReader.isTwinCompleted(user)) {
            throw new BusinessException(ErrorCode.TWIN_NOT_COMPLETED);
        }

        Optional<TwinUpdate> inProgress = twinUpdateRepository.findWithLockByUserIdAndStatus(userId, TwinUpdateStatus.IN_PROGRESS);
        if (inProgress.isPresent()) {
            if (!command.restart()) {
                return result(inProgress.get());
            }
            discard(inProgress.get());
        } else if (twinUpdateReader.nextUpdatableAt(user) != null) {
            throw new BusinessException(ErrorCode.TWIN_UPDATE_NOT_AVAILABLE);
        }

        return result(twinUpdateRepository.save(TwinUpdate.create(userId)));
    }

    public MeTwinUpdateResult twinUpdate(Long userId, UUID updateId) {
        return result(twinUpdateReader.findOwned(userId, updateId));
    }

    @Transactional
    public void submitSurveyAnswer(Long userId, MeSubmitTwinUpdateSurveyAnswerCommand command) {
        TwinUpdate twinUpdate = twinUpdateReader.lockInProgress(userId, command.updateId());

        Integer qId = command.answer().qId();
        SurveyOptionName answerValue = command.answer().optionName();

        if (surveyLoader.getQuestion(qId) == null) {
            throw new BusinessException(ErrorCode.SURVEY_QUESTION_NOT_FOUND, "존재하지 않는 질문입니다: " + qId);
        }

        twinUpdateSurveyAnswerRepository.upsert(twinUpdate.getId(), qId, answerValue.name());

        if (surveyLoader.isLastQuestion(qId)
                && !twinUpdateReader.isSurveyCompleted(twinUpdateReader.answersToCurrentQuestions(twinUpdate.getId()))) {
            throw new BusinessException(ErrorCode.SURVEY_ANSWERS_INCOMPLETE);
        }
    }

    @Transactional
    public void saveInterests(Long userId, MeSaveTwinUpdateInterestsCommand command) {
        TwinUpdate twinUpdate = twinUpdateReader.lockInProgress(userId, command.updateId());

        twinUpdateInterestRepository.deleteAllByTwinUpdateId(twinUpdate.getId());

        Instant now = Instant.now();
        for (String interest : command.interests()) {
            twinUpdateInterestRepository.save(TwinUpdateInterest.create(twinUpdate.getId(), interest, now));
        }
    }

    @Transactional
    public MeCompleteTwinUpdateResult complete(Long userId, MeCompleteTwinUpdateCommand command) {
        TwinUpdate twinUpdate = twinUpdateReader.lockOwned(userId, command.updateId());

        if (twinUpdate.isCompleted()) {
            return completeResult(twinUpdate);
        }
        if (!twinUpdate.isInProgress()) {
            throw new BusinessException(ErrorCode.TWIN_UPDATE_STALE);
        }

        List<TwinUpdateSurveyAnswer> answers = twinUpdateReader.answersToCurrentQuestions(twinUpdate.getId());
        if (!twinUpdateReader.isSurveyCompleted(answers)) {
            throw new BusinessException(ErrorCode.SURVEY_ANSWERS_INCOMPLETE);
        }

        List<String> interests = twinUpdateReader.interests(twinUpdate.getId());
        if (interests.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERESTS_NOT_SAVED);
        }

        List<String> details = twinUpdateReader.details(twinUpdate.getId());

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        twinUpdate.complete(now);
        twinUpdateRepository.saveAndFlush(twinUpdate);

        replaceSurvey(userId, answers, now);
        replaceInterests(userId, interests, now);
        for (String detail : details) {
            personaElementRepository.save(PersonaElement.create(userId, PersonaDimension.DETAIL, detail, now));
        }

        eventPublisher.publishEvent(new TwinUpdateCompletedEvent(userId));

        return completeResult(twinUpdate);
    }

    @Transactional
    public void discardInProgress(Long userId) {
        twinUpdateRepository.findWithLockByUserIdAndStatus(userId, TwinUpdateStatus.IN_PROGRESS)
                .ifPresent(this::discard);
    }

    private void discard(TwinUpdate twinUpdate) {
        twinUpdate.discard();
        twinUpdateRepository.saveAndFlush(twinUpdate);

        twinUpdateSurveyAnswerRepository.deleteAllByTwinUpdateId(twinUpdate.getId());
        twinUpdateInterestRepository.deleteAllByTwinUpdateId(twinUpdate.getId());
        twinUpdateAiChatRepository.deleteAllByTwinUpdateId(twinUpdate.getId());
    }

    private void replaceSurvey(Long userId, List<TwinUpdateSurveyAnswer> answers, Instant now) {
        userSurveyAnswerRepository.deleteAllByUserId(userId);
        userSurveyAnswerRepository.saveAll(answers.stream()
                .map(answer -> UserSurveyAnswer.create(userId, answer.getQuestionId(), answer.getOptionName()))
                .toList());

        List<PersonaElement> personaElements = twinUpdateReader.surveyElements(userId, answers, now);
        Set<PersonaDimension> dimensions = personaElements.stream()
                .map(PersonaElement::getDimension)
                .collect(Collectors.toSet());

        personaElementRepository.deleteByUserIdAndDimensionIn(userId, dimensions);
        personaElementRepository.saveAll(personaElements);
    }

    private void replaceInterests(Long userId, List<String> interests, Instant now) {
        personaElementRepository.deleteByUserIdAndDimension(userId, PersonaDimension.INTEREST);
        for (String interest : interests) {
            personaElementRepository.save(PersonaElement.create(userId, PersonaDimension.INTEREST, interest, now));
        }
    }

    private MeTwinUpdateResult result(TwinUpdate twinUpdate) {
        boolean isSurveyCompleted = twinUpdateReader.isSurveyCompleted(twinUpdateReader.answersToCurrentQuestions(twinUpdate.getId()));
        boolean isInterestsCompleted = twinUpdateInterestRepository.existsByTwinUpdateId(twinUpdate.getId());

        return new MeTwinUpdateResult(
                twinUpdate.getUpdateId(),
                twinUpdate.getStatus(),
                isSurveyCompleted,
                isInterestsCompleted,
                twinUpdate.getCompletedAt(),
                twinUpdate.isCompleted() ? twinUpdate.getCompletedAt().plus(TwinUpdateReader.UPDATE_INTERVAL) : null
        );
    }

    private MeCompleteTwinUpdateResult completeResult(TwinUpdate twinUpdate) {
        return new MeCompleteTwinUpdateResult(
                twinUpdate.getUpdateId(),
                twinUpdate.getCompletedAt(),
                twinUpdate.getCompletedAt().plus(TwinUpdateReader.UPDATE_INTERVAL)
        );
    }
}
