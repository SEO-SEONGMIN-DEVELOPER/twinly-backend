package com.nidus.twinly.twinupdate.service;

import com.nidus.twinly.aichat.domain.AiChatSender;
import com.nidus.twinly.aichat.entity.TwinUpdateAiChat;
import com.nidus.twinly.aichat.repository.TwinUpdateAiChatRepository;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.survey.SurveyLoader;
import com.nidus.twinly.common.survey.SurveyQuestion;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.twinupdate.domain.TwinUpdateStatus;
import com.nidus.twinly.twinupdate.entity.TwinUpdate;
import com.nidus.twinly.twinupdate.entity.TwinUpdateInterest;
import com.nidus.twinly.twinupdate.entity.TwinUpdateSurveyAnswer;
import com.nidus.twinly.twinupdate.repository.TwinUpdateInterestRepository;
import com.nidus.twinly.twinupdate.repository.TwinUpdateRepository;
import com.nidus.twinly.twinupdate.repository.TwinUpdateSurveyAnswerRepository;
import com.nidus.twinly.user.entity.PersonaElement;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PersonaElementRepository;
import com.nidus.twinly.user.repository.UserSurveyAnswerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class TwinUpdateReader {

    public static final Duration UPDATE_INTERVAL = Duration.ofDays(14);

    private final SurveyLoader surveyLoader;

    private final TwinUpdateRepository twinUpdateRepository;
    private final TwinUpdateSurveyAnswerRepository twinUpdateSurveyAnswerRepository;
    private final TwinUpdateInterestRepository twinUpdateInterestRepository;
    private final TwinUpdateAiChatRepository twinUpdateAiChatRepository;
    private final UserSurveyAnswerRepository userSurveyAnswerRepository;
    private final PersonaElementRepository personaElementRepository;

    public TwinUpdate findOwned(Long userId, UUID updateId) {
        return twinUpdateRepository.findByUpdateId(updateId)
                .filter(twinUpdate -> twinUpdate.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.TWIN_UPDATE_NOT_FOUND));
    }

    public TwinUpdate lockOwned(Long userId, UUID updateId) {
        return twinUpdateRepository.findWithLockByUpdateId(updateId)
                .filter(twinUpdate -> twinUpdate.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.TWIN_UPDATE_NOT_FOUND));
    }

    public TwinUpdate lockInProgress(Long userId, UUID updateId) {
        TwinUpdate twinUpdate = lockOwned(userId, updateId);

        return switch (twinUpdate.getStatus()) {
            case IN_PROGRESS -> twinUpdate;
            case COMPLETED -> throw new BusinessException(ErrorCode.TWIN_UPDATE_ALREADY_COMPLETED);
            case DISCARDED -> throw new BusinessException(ErrorCode.TWIN_UPDATE_STALE);
        };
    }

    public boolean isTwinCompleted(User user) {
        int answeredCount = (int) userSurveyAnswerRepository.findAllByUserId(user.getId()).stream()
                .filter(answer -> surveyLoader.getQuestion(answer.getQuestionId()) != null)
                .count();

        return answeredCount == surveyLoader.getAllQuestions().size()
                && personaElementRepository.existsByUserIdAndDimension(user.getId(), PersonaDimension.INTEREST)
                && user.getAiChatCompletedAt() != null;
    }

    public Instant nextUpdatableAt(User user) {
        Instant lastUpdatedAt = twinUpdateRepository
                .findFirstByUserIdAndStatusOrderByCompletedAtDesc(user.getId(), TwinUpdateStatus.COMPLETED)
                .map(TwinUpdate::getCompletedAt)
                .orElse(null);

        Instant lastCompletedAt = Stream.of(user.getAiChatCompletedAt(), lastUpdatedAt)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
        if (lastCompletedAt == null) {
            return null;
        }

        Instant nextUpdatableAt = lastCompletedAt.plus(UPDATE_INTERVAL);
        return nextUpdatableAt.isAfter(Instant.now()) ? nextUpdatableAt : null;
    }

    public List<TwinUpdateSurveyAnswer> answersToCurrentQuestions(Long twinUpdateId) {
        return twinUpdateSurveyAnswerRepository.findAllByTwinUpdateId(twinUpdateId).stream()
                .filter(answer -> surveyLoader.getQuestion(answer.getQuestionId()) != null)
                .toList();
    }

    public boolean isSurveyCompleted(List<TwinUpdateSurveyAnswer> answersToCurrentQuestions) {
        return answersToCurrentQuestions.size() == surveyLoader.getAllQuestions().size();
    }

    public List<PersonaElement> surveyElements(Long userId, List<TwinUpdateSurveyAnswer> answers, Instant now) {
        return answers.stream()
                .map(answer -> {
                    SurveyQuestion question = surveyLoader.getQuestion(answer.getQuestionId());
                    return PersonaElement.create(userId, question.dimension(), question.traitFor(answer.getOptionName()), now);
                })
                .toList();
    }

    public List<String> details(Long twinUpdateId) {
        List<TwinUpdateAiChat> chats = twinUpdateAiChatRepository.findByTwinUpdateIdOrderByTurnIndexAscSenderDesc(twinUpdateId);

        List<String> details = new ArrayList<>();
        for (int i = 0; i + 1 < chats.size(); i++) {
            TwinUpdateAiChat question = chats.get(i);
            TwinUpdateAiChat answer = chats.get(i + 1);
            if (question.getSender() == AiChatSender.AI
                    && answer.getSender() == AiChatSender.USER
                    && question.getTurnIndex().equals(answer.getTurnIndex())) {
                details.add("%s: %s".formatted(question.getMessage(), answer.getMessage()));
            }
        }
        return details;
    }

    public List<String> interests(Long twinUpdateId) {
        return twinUpdateInterestRepository.findAllByTwinUpdateIdOrderByIdAsc(twinUpdateId).stream()
                .map(TwinUpdateInterest::getInterest)
                .toList();
    }
}
