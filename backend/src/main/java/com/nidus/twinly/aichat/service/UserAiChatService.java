package com.nidus.twinly.aichat.service;

import com.nidus.twinly.aichat.domain.AiChatSender;
import com.nidus.twinly.aichat.entity.AiChat;
import com.nidus.twinly.aichat.event.AiChatCompletedEvent;
import com.nidus.twinly.aichat.prompt.AiChatPromptBuilder;
import com.nidus.twinly.aichat.prompt.PersonaTrait;
import com.nidus.twinly.aichat.repository.AiChatRepository;
import com.nidus.twinly.common.aws.bedrock.BedrockService;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.me.dto.command.MeAiChatMessageCommand;
import com.nidus.twinly.me.dto.result.MeAiChatMessageResult;
import com.nidus.twinly.me.dto.result.MeAiChatStartResult;
import com.nidus.twinly.user.entity.PersonaElement;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PersonaElementRepository;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.nidus.twinly.aichat.service.AiChatService.LAST_MESSAGE;
import static com.nidus.twinly.aichat.service.AiChatService.MAX_TURN_INDEX;
import static com.nidus.twinly.aichat.service.AiChatService.RESTART_TURN_INDEX;

@Service
@RequiredArgsConstructor
public class UserAiChatService {

    private final BedrockService bedrockService;
    private final AiChatPromptBuilder aiChatPromptBuilder;

    private final UserRepository userRepository;
    private final AiChatRepository aiChatRepository;
    private final PersonaElementRepository personaElementRepository;

    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public MeAiChatStartResult aiChatStart(Long userId) {
        int turnIndex = 0;

        Optional<AiChat> started = aiChatRepository.findByUserIdAndTurnIndexAndSender(userId, turnIndex, AiChatSender.AI);
        if (started.isPresent()) {
            return new MeAiChatStartResult(started.get().getMessage(), turnIndex, false);
        }

        User user = findUser(userId);

        String prompt = aiChatPromptBuilder.interestPrompt(user.getAffiliation(), traits(userId), List.of());
        String message = bedrockService.converse(prompt);

        aiChatRepository.save(AiChat.create(userId, AiChatSender.AI, message, turnIndex, Instant.now()));

        return new MeAiChatStartResult(message, turnIndex, false);
    }

    @Transactional
    public MeAiChatMessageResult aiChatMessage(Long userId, MeAiChatMessageCommand command) {
        AiChat aiQuestion = aiChatRepository.findByUserIdAndTurnIndexAndSender(userId, command.turnIndex(), AiChatSender.AI)
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_QUESTION_NOT_FOUND, "해당 턴의 AI 질문이 존재하지 않습니다: " + command.turnIndex()));

        if (aiChatRepository.findByUserIdAndTurnIndexAndSender(userId, command.turnIndex(), AiChatSender.USER).isPresent()) {
            if (command.turnIndex() >= MAX_TURN_INDEX) {
                return new MeAiChatMessageResult(LAST_MESSAGE, command.turnIndex(), true);
            }

            int nextTurnIndex = command.turnIndex() + 1;
            AiChat nextQuestion = aiChatRepository.findByUserIdAndTurnIndexAndSender(userId, nextTurnIndex, AiChatSender.AI)
                    .orElseThrow(() -> new BusinessException(ErrorCode.AI_QUESTION_NOT_FOUND, "다음 턴의 AI 질문이 존재하지 않습니다: " + nextTurnIndex));

            return new MeAiChatMessageResult(nextQuestion.getMessage(), nextTurnIndex, false);
        }

        Instant now = Instant.now();
        aiChatRepository.save(AiChat.create(userId, AiChatSender.USER, command.message(), command.turnIndex(), now));

        String detail = "%s: %s".formatted(aiQuestion.getMessage(), command.message());
        personaElementRepository.save(PersonaElement.create(userId, PersonaDimension.DETAIL, detail, now));

        if (command.turnIndex() >= MAX_TURN_INDEX) {
            return new MeAiChatMessageResult(LAST_MESSAGE, command.turnIndex(), true);
        }

        int nextTurnIndex = command.turnIndex() + 1;

        User user = findUser(userId);
        String nextQuestionPrompt = nextTurnIndex == RESTART_TURN_INDEX
                ? aiChatPromptBuilder.interestPrompt(user.getAffiliation(), traits(userId), askedQuestions(userId))
                : aiChatPromptBuilder.followUpPrompt(user.getAffiliation(), traits(userId), aiQuestion.getMessage(), command.message());
        String message = bedrockService.converse(nextQuestionPrompt);

        aiChatRepository.save(AiChat.create(userId, AiChatSender.AI, message, nextTurnIndex, Instant.now()));

        return new MeAiChatMessageResult(message, nextTurnIndex, false);
    }

    @Transactional
    public void aiChatComplete(Long userId) {
        if (userRepository.markAiChatCompleted(userId, Instant.now()) == 1) {
            eventPublisher.publishEvent(new AiChatCompletedEvent(userId));
        }
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private List<PersonaTrait> traits(Long userId) {
        return personaElementRepository.findAllByUserIdOrderByIdAsc(userId).stream()
                .map(element -> new PersonaTrait(element.getDimension(), element.getExplanation()))
                .toList();
    }

    private List<String> askedQuestions(Long userId) {
        return aiChatRepository.findByUserIdOrderByTurnIndexAscSenderDesc(userId).stream()
                .filter(chat -> chat.getSender() == AiChatSender.AI)
                .map(AiChat::getMessage)
                .toList();
    }
}
