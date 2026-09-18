package com.nidus.twinly.aichat.service;

import com.nidus.twinly.aichat.domain.AiChatSender;
import com.nidus.twinly.aichat.entity.AnonSessionAiChat;
import com.nidus.twinly.aichat.prompt.AiChatPromptBuilder;
import com.nidus.twinly.aichat.prompt.PersonaTrait;
import com.nidus.twinly.aichat.repository.AnonSessionAiChatRepository;
import com.nidus.twinly.anon.dto.snapshot.AnonSessionSnapshot;
import com.nidus.twinly.anon.entity.AnonSessionPersonaElement;
import com.nidus.twinly.anon.repository.AnonSessionPersonaElementRepository;
import com.nidus.twinly.common.aws.bedrock.BedrockService;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.onboarding.dto.command.OnboardingAiChatMessageCommand;
import com.nidus.twinly.onboarding.dto.result.OnboardingAiChatMessageResult;
import com.nidus.twinly.onboarding.dto.result.OnboardingAiChatStartResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AiChatService {

    static final int MAX_TURN_INDEX = 7;
    static final int RESTART_TURN_INDEX = 4;
    static final String LAST_MESSAGE = "지금까지 이야기 들려줘서 고마워!";

    private final BedrockService bedrockService;
    private final AiChatPromptBuilder aiChatPromptBuilder;

    private final AnonSessionAiChatRepository anonSessionAiChatRepository;
    private final AnonSessionPersonaElementRepository anonSessionPersonaElementRepository;

    @Transactional
    public OnboardingAiChatStartResult aiChatStart(AnonSessionSnapshot anonSessionSnapshot) {
        Long anonSessionId = anonSessionSnapshot.id();
        int turnIndex = 0;

        Optional<AnonSessionAiChat> started = anonSessionAiChatRepository
                .findByAnonSessionIdAndTurnIndexAndSender(anonSessionId, turnIndex, AiChatSender.AI);
        if (started.isPresent()) {
            return new OnboardingAiChatStartResult(started.get().getMessage(), turnIndex, false);
        }

        List<AnonSessionPersonaElement> personaElements = anonSessionPersonaElementRepository.findAllByAnonSessionId(anonSessionId);

        String prompt = aiChatPromptBuilder.interestPrompt(anonSessionSnapshot.affiliation(), traits(personaElements), List.of());
        String message = bedrockService.converse(prompt);

        anonSessionAiChatRepository.save(AnonSessionAiChat.create(anonSessionId, AiChatSender.AI, message, turnIndex));

        return new OnboardingAiChatStartResult(message, turnIndex, false);
    }

    @Transactional
    public OnboardingAiChatMessageResult aiChatMessage(AnonSessionSnapshot anonSessionSnapshot, OnboardingAiChatMessageCommand command) {
        Long anonSessionId = anonSessionSnapshot.id();

        AnonSessionAiChat aiQuestion = anonSessionAiChatRepository.findByAnonSessionIdAndTurnIndexAndSender(anonSessionId, command.turnIndex(), AiChatSender.AI)
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_QUESTION_NOT_FOUND, "해당 턴의 AI 질문이 존재하지 않습니다: " + command.turnIndex()));

        if (anonSessionAiChatRepository.findByAnonSessionIdAndTurnIndexAndSender(anonSessionId, command.turnIndex(), AiChatSender.USER).isPresent()) {
            if (command.turnIndex() >= MAX_TURN_INDEX) {
                return new OnboardingAiChatMessageResult(LAST_MESSAGE, command.turnIndex(), true);
            }

            int nextTurnIndex = command.turnIndex() + 1;
            AnonSessionAiChat nextQuestion = anonSessionAiChatRepository
                    .findByAnonSessionIdAndTurnIndexAndSender(anonSessionId, nextTurnIndex, AiChatSender.AI)
                    .orElseThrow(() -> new BusinessException(ErrorCode.AI_QUESTION_NOT_FOUND, "다음 턴의 AI 질문이 존재하지 않습니다: " + nextTurnIndex));

            return new OnboardingAiChatMessageResult(nextQuestion.getMessage(), nextTurnIndex, false);
        }

        anonSessionAiChatRepository.save(AnonSessionAiChat.create(anonSessionId, AiChatSender.USER, command.message(), command.turnIndex()));

        String detail = "%s: %s".formatted(aiQuestion.getMessage(), command.message());
        anonSessionPersonaElementRepository.save(AnonSessionPersonaElement.create(anonSessionId, PersonaDimension.DETAIL, detail));

        if (command.turnIndex() >= MAX_TURN_INDEX) {
            return new OnboardingAiChatMessageResult(LAST_MESSAGE, command.turnIndex(), true);
        }

        int nextTurnIndex = command.turnIndex() + 1;

        List<AnonSessionPersonaElement> personaElements = anonSessionPersonaElementRepository.findAllByAnonSessionId(anonSessionId);
        String nextQuestionPrompt = nextTurnIndex == RESTART_TURN_INDEX
                ? aiChatPromptBuilder.interestPrompt(anonSessionSnapshot.affiliation(), traits(personaElements), askedQuestions(anonSessionId))
                : aiChatPromptBuilder.followUpPrompt(anonSessionSnapshot.affiliation(), traits(personaElements), aiQuestion.getMessage(), command.message());
        String message = bedrockService.converse(nextQuestionPrompt);

        anonSessionAiChatRepository.save(AnonSessionAiChat.create(anonSessionId, AiChatSender.AI, message, nextTurnIndex));

        return new OnboardingAiChatMessageResult(message, nextTurnIndex, false);
    }

    private List<String> askedQuestions(Long anonSessionId) {
        return anonSessionAiChatRepository.findByAnonSessionIdOrderByTurnIndexAscSenderDesc(anonSessionId).stream()
                .filter(chat -> chat.getSender() == AiChatSender.AI)
                .map(AnonSessionAiChat::getMessage)
                .toList();
    }

    private List<PersonaTrait> traits(List<AnonSessionPersonaElement> personaElements) {
        return personaElements.stream()
                .map(element -> new PersonaTrait(element.getDimension(), element.getExplanation()))
                .toList();
    }
}
