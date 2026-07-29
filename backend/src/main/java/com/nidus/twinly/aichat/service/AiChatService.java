package com.nidus.twinly.aichat.service;

import com.nidus.twinly.aichat.domain.AiChatSender;
import com.nidus.twinly.aichat.entity.AiChat;
import com.nidus.twinly.aichat.repository.AiChatRepository;
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
    private static final String LAST_MESSAGE = "지금까지 이야기 들려줘서 고마워!";

    private final BedrockService bedrockService;

    private final AiChatRepository aiChatRepository;
    private final AnonSessionPersonaElementRepository anonSessionPersonaElementRepository;

    @Transactional
    public OnboardingAiChatStartResult aiChatStart(AnonSessionSnapshot anonSessionSnapshot) {
        Long anonSessionId = anonSessionSnapshot.id();
        int turnIndex = 0;

        // 이미 시작된 세션이면 저장된 첫 질문을 그대로 돌려준다.
        // 모델 호출 전에 검사해야 재요청마다 비용이 새지 않는다.
        Optional<AiChat> started = aiChatRepository
                .findByAnonSessionIdAndTurnIndexAndSender(anonSessionId, turnIndex, AiChatSender.AI);
        if (started.isPresent()) {
            return new OnboardingAiChatStartResult(started.get().getMessage(), turnIndex, false);
        }

        List<AnonSessionPersonaElement> personaElements = anonSessionPersonaElementRepository.findAllByAnonSessionId(anonSessionId);

        String prompt = buildPersonaPrompt(anonSessionSnapshot, personaElements);
        String message = bedrockService.converse(prompt);

        aiChatRepository.save(AiChat.create(anonSessionId, AiChatSender.AI, message, turnIndex));

        return new OnboardingAiChatStartResult(message, turnIndex, false);
    }

    private String buildPersonaPrompt(AnonSessionSnapshot session, List<AnonSessionPersonaElement> personaElements) {
        StringBuilder sb = new StringBuilder();
        sb.append("당신은 사용자와 대화를 나누며 그 사람을 더 깊이 이해하려는 인터뷰어입니다.\n");
        sb.append("아래는 지금까지 파악한 사용자의 정보입니다.\n\n");

        sb.append("[소속 정보]\n");
        if (session.affiliation() != null) {
            sb.append("- 소속: ").append(session.affiliation()).append("\n");
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
    public OnboardingAiChatMessageResult aiChatMessage(AnonSessionSnapshot anonSessionSnapshot, OnboardingAiChatMessageCommand command) {
        Long anonSessionId = anonSessionSnapshot.id();

        AiChat aiQuestion = aiChatRepository.findByAnonSessionIdAndTurnIndexAndSender(anonSessionId, command.turnIndex(), AiChatSender.AI)
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_QUESTION_NOT_FOUND, "해당 턴의 AI 질문이 존재하지 않습니다: " + command.turnIndex()));

        // 이미 답한 턴이면 그때 만들어 둔 다음 질문을 그대로 돌려준다.
        // 답변 저장과 다음 질문 생성이 같은 트랜잭션이라, 답변이 있으면 다음 질문도 반드시 있다.
        if (aiChatRepository.findByAnonSessionIdAndTurnIndexAndSender(anonSessionId, command.turnIndex(), AiChatSender.USER).isPresent()) {
            if (command.turnIndex() >= MAX_TURN_INDEX) {
                return new OnboardingAiChatMessageResult(LAST_MESSAGE, command.turnIndex(), true);
            }

            int nextTurnIndex = command.turnIndex() + 1;
            AiChat nextQuestion = aiChatRepository
                    .findByAnonSessionIdAndTurnIndexAndSender(anonSessionId, nextTurnIndex, AiChatSender.AI)
                    .orElseThrow(() -> new BusinessException(ErrorCode.AI_QUESTION_NOT_FOUND, "다음 턴의 AI 질문이 존재하지 않습니다: " + nextTurnIndex));

            return new OnboardingAiChatMessageResult(nextQuestion.getMessage(), nextTurnIndex, false);
        }

        aiChatRepository.save(AiChat.create(anonSessionId, AiChatSender.USER, command.message(), command.turnIndex()));

        String detail = "%s: %s".formatted(aiQuestion.getMessage(), command.message());
        anonSessionPersonaElementRepository.save(AnonSessionPersonaElement.create(anonSessionId, PersonaDimension.DETAIL, detail));

        if (command.turnIndex() >= MAX_TURN_INDEX) {
            return new OnboardingAiChatMessageResult(LAST_MESSAGE, command.turnIndex(), true);
        }

        List<AnonSessionPersonaElement> personaElements = anonSessionPersonaElementRepository.findAllByAnonSessionId(anonSessionId);
        String nextQuestionPrompt = buildFollowUpPrompt(anonSessionSnapshot, personaElements, aiQuestion.getMessage(), command.message());
        String message = bedrockService.converse(nextQuestionPrompt);

        int nextTurnIndex = command.turnIndex() + 1;
        aiChatRepository.save(AiChat.create(anonSessionId, AiChatSender.AI, message, nextTurnIndex));

        return new OnboardingAiChatMessageResult(message, nextTurnIndex, false);
    }

    private String buildFollowUpPrompt(AnonSessionSnapshot session, List<AnonSessionPersonaElement> personaElements, String previousQuestion, String userAnswer) {
        StringBuilder sb = new StringBuilder();
        sb.append("당신은 사용자와 대화를 나누며 그 사람을 더 깊이 이해하려는 인터뷰어입니다.\n");
        sb.append("아래는 지금까지 파악한 사용자의 정보입니다.\n\n");

        sb.append("[소속 정보]\n");
        if (session.affiliation() != null) {
            sb.append("- 소속: ").append(session.affiliation()).append("\n");
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
