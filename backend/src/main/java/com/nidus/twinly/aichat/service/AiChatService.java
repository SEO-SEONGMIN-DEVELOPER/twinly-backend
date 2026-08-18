package com.nidus.twinly.aichat.service;

import com.nidus.twinly.aichat.domain.AiChatSender;
import com.nidus.twinly.aichat.entity.AnonSessionAiChat;
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

    private static final int MAX_TURN_INDEX = 7;
    private static final int RESTART_TURN_INDEX = 4;
    private static final String LAST_MESSAGE = "지금까지 이야기 들려줘서 고마워!";

    private final BedrockService bedrockService;

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

        String prompt = buildInterestPrompt(anonSessionSnapshot, personaElements, List.of());
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
                ? buildInterestPrompt(anonSessionSnapshot, personaElements, askedQuestions(anonSessionId))
                : buildFollowUpPrompt(anonSessionSnapshot, personaElements, aiQuestion.getMessage(), command.message());
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

    private String buildInterestPrompt(AnonSessionSnapshot session, List<AnonSessionPersonaElement> personaElements, List<String> askedQuestions) {
        List<String> interests = personaElements.stream()
                .filter(element -> element.getDimension() == PersonaDimension.INTEREST)
                .map(AnonSessionPersonaElement::getExplanation)
                .toList();

        StringBuilder sb = new StringBuilder();
        appendContext(sb, session, personaElements);

        if (!askedQuestions.isEmpty()) {
            sb.append("\n[이미 물어본 질문]\n");
            for (String askedQuestion : askedQuestions) {
                sb.append("- ").append(askedQuestion).append("\n");
            }
        }

        if (interests.isEmpty()) {
            sb.append("\n위 정보를 참고해서, 사용자를 더 깊이 이해할 수 있는 자연스러운 질문을 한국어로 하나만 물어보세요.\n");
        } else {
            sb.append("\n위 관심사 중 하나를 골라, 그 관심사에 대한 질문을 한국어로 하나만 물어보세요.\n");
        }
        if (!askedQuestions.isEmpty()) {
            sb.append("이미 물어본 질문과 이어지지 않는, 완전히 새로운 주제로 대화를 다시 시작하는 질문이어야 합니다.\n");
        }
        appendToneGuide(sb);

        return sb.toString();
    }

    private String buildFollowUpPrompt(AnonSessionSnapshot session, List<AnonSessionPersonaElement> personaElements, String previousQuestion, String userAnswer) {
        StringBuilder sb = new StringBuilder();
        appendContext(sb, session, personaElements);

        sb.append("\n[방금 나눈 대화]\n");
        sb.append("나의 질문: ").append(previousQuestion).append("\n");
        sb.append("사용자의 답변: ").append(userAnswer).append("\n");

        sb.append("\n위 정보와 사용자의 방금 답변을 참고해서, 사용자를 더 깊이 이해할 수 있는 자연스러운 후속 질문을 한국어로 하나만 물어보세요.\n");
        appendToneGuide(sb);

        return sb.toString();
    }

    private void appendContext(StringBuilder sb, AnonSessionSnapshot session, List<AnonSessionPersonaElement> personaElements) {
        sb.append("당신은 사용자와 대화를 나누며 그 사람을 더 깊이 이해하려는 인터뷰어입니다.\n");
        sb.append("아래는 지금까지 파악한 사용자의 정보입니다.\n\n");

        sb.append("[소속 정보]\n");
        if (session.affiliation() != null) {
            sb.append("- 소속: ").append(session.affiliation()).append("\n");
        }

        sb.append("\n[관심사]\n");
        personaElements.stream()
                .filter(element -> element.getDimension() == PersonaDimension.INTEREST)
                .forEach(element -> sb.append("- ").append(element.getExplanation()).append("\n"));

        sb.append("\n[성격 특성]\n");
        personaElements.stream()
                .filter(element -> element.getDimension() != PersonaDimension.INTEREST)
                .forEach(element -> sb.append("- ").append(element.getDimension()).append(": ").append(element.getExplanation()).append("\n"));
    }

    private void appendToneGuide(StringBuilder sb) {
        sb.append("공식적인 인터뷰어같지 않은 친근한 말투와 반말을 사용하세요.\n");
        sb.append("질문 외에 다른 설명은 하지 마세요.\n");
    }
}
