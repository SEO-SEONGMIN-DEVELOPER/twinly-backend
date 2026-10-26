package com.nidus.twinly.aichat.service;

import com.nidus.twinly.aichat.domain.AiChatSender;
import com.nidus.twinly.aichat.entity.AiChat;
import com.nidus.twinly.aichat.entity.TwinUpdateAiChat;
import com.nidus.twinly.aichat.prompt.AiChatPromptBuilder;
import com.nidus.twinly.aichat.prompt.PersonaTrait;
import com.nidus.twinly.aichat.repository.AiChatRepository;
import com.nidus.twinly.aichat.repository.TwinUpdateAiChatRepository;
import com.nidus.twinly.common.aws.bedrock.BedrockService;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.me.dto.command.MeSendTwinUpdateAiChatMessageCommand;
import com.nidus.twinly.me.dto.command.MeStartTwinUpdateAiChatCommand;
import com.nidus.twinly.me.dto.result.MeAiChatMessageResult;
import com.nidus.twinly.me.dto.result.MeAiChatStartResult;
import com.nidus.twinly.twinupdate.entity.TwinUpdate;
import com.nidus.twinly.twinupdate.entity.TwinUpdateSurveyAnswer;
import com.nidus.twinly.twinupdate.service.TwinUpdateReader;
import com.nidus.twinly.user.entity.PersonaElement;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PersonaElementRepository;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static com.nidus.twinly.aichat.service.AiChatService.LAST_MESSAGE;
import static com.nidus.twinly.aichat.service.AiChatService.MAX_TURN_INDEX;
import static com.nidus.twinly.aichat.service.AiChatService.TOPIC_TURN_COUNT;

@Service
@RequiredArgsConstructor
public class TwinUpdateAiChatService {

    private final BedrockService bedrockService;
    private final AiChatPromptBuilder aiChatPromptBuilder;
    private final TwinUpdateReader twinUpdateReader;

    private final UserRepository userRepository;
    private final AiChatRepository aiChatRepository;
    private final TwinUpdateAiChatRepository twinUpdateAiChatRepository;
    private final PersonaElementRepository personaElementRepository;

    @Transactional
    public MeAiChatStartResult aiChatStart(Long userId, MeStartTwinUpdateAiChatCommand command) {
        TwinUpdate twinUpdate = twinUpdateReader.lockInProgress(userId, command.updateId());
        int turnIndex = 0;

        Optional<TwinUpdateAiChat> started = twinUpdateAiChatRepository
                .findByTwinUpdateIdAndTurnIndexAndSender(twinUpdate.getId(), turnIndex, AiChatSender.AI);
        if (started.isPresent()) {
            return new MeAiChatStartResult(started.get().getMessage(), turnIndex, false);
        }

        String message = bedrockService.converse(topicPrompt(findUser(userId), twinUpdate, turnIndex));

        twinUpdateAiChatRepository.save(TwinUpdateAiChat.create(twinUpdate.getId(), AiChatSender.AI, message, turnIndex));

        return new MeAiChatStartResult(message, turnIndex, false);
    }

    @Transactional
    public MeAiChatMessageResult aiChatMessage(Long userId, MeSendTwinUpdateAiChatMessageCommand command) {
        TwinUpdate twinUpdate = twinUpdateReader.lockInProgress(userId, command.updateId());
        Long twinUpdateId = twinUpdate.getId();

        TwinUpdateAiChat aiQuestion = twinUpdateAiChatRepository.findByTwinUpdateIdAndTurnIndexAndSender(twinUpdateId, command.turnIndex(), AiChatSender.AI)
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_QUESTION_NOT_FOUND, "해당 턴의 AI 질문이 존재하지 않습니다: " + command.turnIndex()));

        if (twinUpdateAiChatRepository.findByTwinUpdateIdAndTurnIndexAndSender(twinUpdateId, command.turnIndex(), AiChatSender.USER).isPresent()) {
            if (command.turnIndex() >= MAX_TURN_INDEX) {
                return new MeAiChatMessageResult(LAST_MESSAGE, command.turnIndex(), true);
            }

            int nextTurnIndex = command.turnIndex() + 1;
            TwinUpdateAiChat nextQuestion = twinUpdateAiChatRepository.findByTwinUpdateIdAndTurnIndexAndSender(twinUpdateId, nextTurnIndex, AiChatSender.AI)
                    .orElseThrow(() -> new BusinessException(ErrorCode.AI_QUESTION_NOT_FOUND, "다음 턴의 AI 질문이 존재하지 않습니다: " + nextTurnIndex));

            return new MeAiChatMessageResult(nextQuestion.getMessage(), nextTurnIndex, false);
        }

        twinUpdateAiChatRepository.saveAndFlush(TwinUpdateAiChat.create(twinUpdateId, AiChatSender.USER, command.message(), command.turnIndex()));

        if (command.turnIndex() >= MAX_TURN_INDEX) {
            return new MeAiChatMessageResult(LAST_MESSAGE, command.turnIndex(), true);
        }

        int nextTurnIndex = command.turnIndex() + 1;

        User user = findUser(userId);
        String nextQuestionPrompt = nextTurnIndex % TOPIC_TURN_COUNT == 0
                ? topicPrompt(user, twinUpdate, nextTurnIndex)
                : aiChatPromptBuilder.followUpPrompt(user.getAffiliation(), traits(twinUpdate), aiQuestion.getMessage(), command.message());
        String message = bedrockService.converse(nextQuestionPrompt);

        twinUpdateAiChatRepository.save(TwinUpdateAiChat.create(twinUpdateId, AiChatSender.AI, message, nextTurnIndex));

        return new MeAiChatMessageResult(message, nextTurnIndex, false);
    }

    private String topicPrompt(User user, TwinUpdate twinUpdate, int turnIndex) {
        List<String> newInterests = newInterests(twinUpdate);
        int topicIndex = turnIndex / TOPIC_TURN_COUNT;
        String focusInterest = topicIndex < newInterests.size() ? newInterests.get(topicIndex) : null;

        return aiChatPromptBuilder.interestPrompt(user.getAffiliation(), traits(twinUpdate), askedQuestions(twinUpdate), focusInterest);
    }

    private List<String> newInterests(TwinUpdate twinUpdate) {
        Set<String> currentInterests = new HashSet<>(personaElementRepository
                .findAllByUserIdAndDimensionOrderByIdAsc(twinUpdate.getUserId(), PersonaDimension.INTEREST).stream()
                .map(PersonaElement::getExplanation)
                .toList());

        return twinUpdateReader.interests(twinUpdate.getId()).stream()
                .filter(interest -> !currentInterests.contains(interest))
                .distinct()
                .toList();
    }

    private List<PersonaTrait> traits(TwinUpdate twinUpdate) {
        Long userId = twinUpdate.getUserId();
        List<PersonaTrait> traits = new ArrayList<>();
        Set<PersonaDimension> replaced = EnumSet.noneOf(PersonaDimension.class);

        List<TwinUpdateSurveyAnswer> answers = twinUpdateReader.answersToCurrentQuestions(twinUpdate.getId());
        if (twinUpdateReader.isSurveyCompleted(answers)) {
            twinUpdateReader.surveyElements(userId, answers, Instant.now()).forEach(element -> {
                traits.add(new PersonaTrait(element.getDimension(), element.getExplanation()));
                replaced.add(element.getDimension());
            });
        }

        List<String> interests = twinUpdateReader.interests(twinUpdate.getId());
        if (!interests.isEmpty()) {
            interests.forEach(interest -> traits.add(new PersonaTrait(PersonaDimension.INTEREST, interest)));
            replaced.add(PersonaDimension.INTEREST);
        }

        personaElementRepository.findAllByUserIdOrderByIdAsc(userId).stream()
                .filter(element -> !replaced.contains(element.getDimension()))
                .forEach(element -> traits.add(new PersonaTrait(element.getDimension(), element.getExplanation())));

        twinUpdateReader.details(twinUpdate.getId()).forEach(detail -> traits.add(new PersonaTrait(PersonaDimension.DETAIL, detail)));

        return traits;
    }

    private List<String> askedQuestions(TwinUpdate twinUpdate) {
        Stream<String> createdQuestions = aiChatRepository.findByUserIdOrderByTurnIndexAscSenderDesc(twinUpdate.getUserId()).stream()
                .filter(chat -> chat.getSender() == AiChatSender.AI)
                .map(AiChat::getMessage);
        Stream<String> updatedQuestions = twinUpdateAiChatRepository.findCompletedQuestionsByUserId(twinUpdate.getUserId()).stream();
        Stream<String> currentQuestions = twinUpdateAiChatRepository.findByTwinUpdateIdOrderByTurnIndexAscSenderDesc(twinUpdate.getId()).stream()
                .filter(chat -> chat.getSender() == AiChatSender.AI)
                .map(TwinUpdateAiChat::getMessage);

        return Stream.of(createdQuestions, updatedQuestions, currentQuestions)
                .flatMap(questions -> questions)
                .toList();
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
