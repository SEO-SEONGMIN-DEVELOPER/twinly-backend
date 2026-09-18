package com.nidus.twinly.aichat.prompt;

import com.nidus.twinly.common.persona.PersonaDimension;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AiChatPromptBuilder {

    public String interestPrompt(String affiliation, List<PersonaTrait> traits, List<String> askedQuestions) {
        List<String> interests = traits.stream()
                .filter(trait -> trait.dimension() == PersonaDimension.INTEREST)
                .map(PersonaTrait::explanation)
                .toList();

        StringBuilder sb = new StringBuilder();
        appendContext(sb, affiliation, traits);

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

    public String followUpPrompt(String affiliation, List<PersonaTrait> traits, String previousQuestion, String userAnswer) {
        StringBuilder sb = new StringBuilder();
        appendContext(sb, affiliation, traits);

        sb.append("\n[방금 나눈 대화]\n");
        sb.append("나의 질문: ").append(previousQuestion).append("\n");
        sb.append("사용자의 답변: ").append(userAnswer).append("\n");

        sb.append("\n위 정보와 사용자의 방금 답변을 참고해서, 사용자를 더 깊이 이해할 수 있는 자연스러운 후속 질문을 한국어로 하나만 물어보세요.\n");
        appendToneGuide(sb);

        return sb.toString();
    }

    private void appendContext(StringBuilder sb, String affiliation, List<PersonaTrait> traits) {
        sb.append("당신은 사용자와 대화를 나누며 그 사람을 더 깊이 이해하려는 인터뷰어입니다.\n");
        sb.append("아래는 지금까지 파악한 사용자의 정보입니다.\n\n");

        sb.append("[소속 정보]\n");
        if (affiliation != null) {
            sb.append("- 소속: ").append(affiliation).append("\n");
        }

        sb.append("\n[관심사]\n");
        traits.stream()
                .filter(trait -> trait.dimension() == PersonaDimension.INTEREST)
                .forEach(trait -> sb.append("- ").append(trait.explanation()).append("\n"));

        sb.append("\n[성격 특성]\n");
        traits.stream()
                .filter(trait -> trait.dimension() != PersonaDimension.INTEREST)
                .forEach(trait -> sb.append("- ").append(trait.dimension()).append(": ").append(trait.explanation()).append("\n"));
    }

    private void appendToneGuide(StringBuilder sb) {
        sb.append("공식적인 인터뷰어같지 않은 친근한 말투와 반말을 사용하세요.\n");
        sb.append("질문 외에 다른 설명은 하지 마세요.\n");
    }
}
