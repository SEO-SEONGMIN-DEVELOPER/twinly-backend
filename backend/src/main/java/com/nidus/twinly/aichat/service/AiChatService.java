package com.nidus.twinly.aichat.service;

import com.nidus.twinly.aichat.domain.AiChatSender;
import com.nidus.twinly.aichat.entity.AiChat;
import com.nidus.twinly.aichat.repository.AiChatRepository;
import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.anon.entity.AnonSessionPersonaElement;
import com.nidus.twinly.anon.repository.AnonSessionPersonaElementRepository;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import com.nidus.twinly.common.aws.bedrock.BedrockService;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.onboarding.dto.command.OnboardingAiChatMessageCommand;
import com.nidus.twinly.onboarding.dto.result.OnboardingAiChatMessageResult;
import com.nidus.twinly.onboarding.dto.result.OnboardingAiChatStartResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiChatService {

    private static final int MAX_TURN_INDEX = 7;

    private final BedrockService bedrockService;

    private final AiChatRepository aiChatRepository;
    private final AnonSessionRepository anonSessionRepository;
    private final AnonSessionPersonaElementRepository anonSessionPersonaElementRepository;

    @Transactional
    public OnboardingAiChatStartResult aiChatStart(Long anonSessionId) {
        if (aiChatRepository.existsByAnonSessionId(anonSessionId)) {
            AiChat firstMessage = aiChatRepository.findByAnonSessionIdOrderByTurnIndexAscSenderDesc(anonSessionId)
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("이미 시작된 채팅인데 첫 메시지가 없습니다: " + anonSessionId));

            return new OnboardingAiChatStartResult(firstMessage.getMessage(), firstMessage.getTurnIndex(), false);
        }

        AnonSession anonSession = anonSessionRepository.findById(anonSessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 세션입니다"));

        List<AnonSessionPersonaElement> personaElements = anonSessionPersonaElementRepository.findByAnonSessionId(anonSessionId);

        String prompt = buildPersonaPrompt(anonSession, personaElements);
        String reply = bedrockService.converse(prompt);

        int turnIndex = 0;
        aiChatRepository.save(AiChat.create(anonSessionId, AiChatSender.AI, reply, turnIndex));

        return new OnboardingAiChatStartResult(reply, turnIndex, false);
    }

    private String buildPersonaPrompt(AnonSession session, List<AnonSessionPersonaElement> personaElements) {
        StringBuilder sb = new StringBuilder();
        sb.append("당신은 사용자와 대화를 나누며 그 사람을 더 깊이 이해하려는 인터뷰어입니다.\n");
        sb.append("아래는 지금까지 파악한 사용자의 정보입니다.\n\n");

        sb.append("[소속 정보]\n");
        if (session.getAffiliation() != null) {
            sb.append("- 소속: ").append(session.getAffiliation()).append("\n");
        }

        sb.append("\n[성격 특성]\n");
        for (AnonSessionPersonaElement element : personaElements) {
            sb.append("- ").append(element.getDimension()).append(": ").append(element.getExplanation()).append("\n");
        }

        sb.append("\n위 정보를 참고해서, 사용자를 더 깊이 이해할 수 있는 자연스러운 후속 질문을 한국어로 하나만 물어보세요.\n");
        sb.append("공식적인 인터뷰어같지 않은 친근한 말투와 반말을 사용하세요.\n");
        sb.append("질문 외에 다른 설명은 하지 마세요.\n");

        return sb.toString();
    }

    @Transactional
    public OnboardingAiChatMessageResult aiChatMessage(Long anonSessionId, OnboardingAiChatMessageCommand command) {
        if (command.message() == null || command.turnIndex() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
        }

        AnonSession anonSession = anonSessionRepository.findById(anonSessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 세션입니다"));

        AiChat aiQuestion = aiChatRepository.findByAnonSessionIdAndTurnIndexAndSender(anonSessionId, command.turnIndex(), AiChatSender.AI)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "해당 턴의 AI 질문이 존재하지 않습니다: " + command.turnIndex()));

        aiChatRepository.save(AiChat.create(anonSessionId, AiChatSender.USER, command.message(), command.turnIndex()));

        String detail = "%s: %s".formatted(aiQuestion.getMessage(), command.message());
        anonSessionPersonaElementRepository.save(AnonSessionPersonaElement.create(anonSessionId, PersonaDimension.DETAIL, detail));

        if (command.turnIndex() >= MAX_TURN_INDEX) {
            return new OnboardingAiChatMessageResult("지금까지 이야기 들려줘서 고마워!", command.turnIndex(), true);
        }

        List<AnonSessionPersonaElement> personaElements = anonSessionPersonaElementRepository.findByAnonSessionId(anonSessionId);
        String nextQuestionPrompt = buildFollowUpPrompt(anonSession, personaElements, aiQuestion.getMessage(), command.message());
        String reply = bedrockService.converse(nextQuestionPrompt);

        int nextTurnIndex = command.turnIndex() + 1;
        aiChatRepository.save(AiChat.create(anonSessionId, AiChatSender.AI, reply, nextTurnIndex));

        return new OnboardingAiChatMessageResult(reply, nextTurnIndex, false);
    }

    private String buildFollowUpPrompt(AnonSession session, List<AnonSessionPersonaElement> personaElements, String previousQuestion, String userAnswer) {
        StringBuilder sb = new StringBuilder();
        sb.append("당신은 사용자와 대화를 나누며 그 사람을 더 깊이 이해하려는 인터뷰어입니다.\n");
        sb.append("아래는 지금까지 파악한 사용자의 정보입니다.\n\n");

        sb.append("[소속 정보]\n");
        if (session.getAffiliation() != null) {
            sb.append("- 소속: ").append(session.getAffiliation()).append("\n");
        }

        sb.append("\n[성격 특성]\n");
        for (AnonSessionPersonaElement element : personaElements) {
            sb.append("- ").append(element.getDimension()).append(": ").append(element.getExplanation()).append("\n");
        }

        sb.append("\n[방금 나눈 대화]\n");
        sb.append("나의 질문: ").append(previousQuestion).append("\n");
        sb.append("사용자의 답변: ").append(userAnswer).append("\n");

        sb.append("\n위 정보와 사용자의 방금 답변을 참고해서, 사용자를 더 깊이 이해할 수 있는 자연스러운 후속 질문을 한국어로 하나만 물어보세요.\n");
        sb.append("공식적인 인터뷰어같지 않은 친근한 말투와 반말을 사용하세요.\n");
        sb.append("질문 외에 다른 설명은 하지 마세요.\n");

        return sb.toString();
    }
}