package com.nidus.twinly.user.generator;

import com.nidus.twinly.common.aws.bedrock.BedrockService;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.user.entity.PersonaElement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PersonaSummaryGenerator {

    private final BedrockService bedrockService;

    public String generate(String affiliation, List<PersonaElement> personaElements) {
        return bedrockService.converse(buildPrompt(affiliation, personaElements)).strip();
    }

    private String buildPrompt(String affiliation, List<PersonaElement> personaElements) {
        StringBuilder sb = new StringBuilder();
        sb.append("당신은 사용자와 나눈 대화를 바탕으로 그 사람이 어떤 사람인지 한 문장으로 소개하는 작가입니다.\n");
        sb.append("아래는 지금까지 파악한 사용자의 정보입니다.\n\n");

        sb.append("[소속 정보]\n");
        if (affiliation != null) {
            sb.append("- 소속: ").append(affiliation).append("\n");
        }

        sb.append("\n[관심사]\n");
        personaElements.stream()
                .filter(element -> element.getDimension() == PersonaDimension.INTEREST)
                .forEach(element -> sb.append("- ").append(element.getExplanation()).append("\n"));

        sb.append("\n[성격 특성]\n");
        personaElements.stream()
                .filter(element -> element.getDimension() != PersonaDimension.INTEREST
                        && element.getDimension() != PersonaDimension.DETAIL
                        && element.getDimension() != PersonaDimension.SUMMARY)
                .forEach(element -> sb.append("- ").append(element.getDimension()).append(": ").append(element.getExplanation()).append("\n"));

        sb.append("\n[나눈 대화]\n");
        personaElements.stream()
                .filter(element -> element.getDimension() == PersonaDimension.DETAIL)
                .forEach(element -> sb.append("- ").append(element.getExplanation()).append("\n"));

        sb.append("\n위 정보를 바탕으로 이 사람이 어떤 사람인지 한국어 한 문장으로 요약하세요.\n");
        sb.append("반드시 \"~한 사람\"으로 끝나는 형태여야 합니다. (예: 주말마다 북한산에 오르며 사진으로 순간을 남기는 사람)\n");
        sb.append("성격 특성을 그대로 나열하지 말고, 대화에서 드러난 구체적인 모습을 중심으로 쓰세요.\n");
        sb.append("60자 이내로 쓰세요.\n");
        sb.append("요약 문장 외에 다른 설명, 따옴표, 마침표는 붙이지 마세요.\n");

        return sb.toString();
    }
}
