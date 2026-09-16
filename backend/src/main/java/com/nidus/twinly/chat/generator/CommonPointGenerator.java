package com.nidus.twinly.chat.generator;

import com.nidus.twinly.common.aws.bedrock.BedrockService;
import com.nidus.twinly.common.logging.WarnLog;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.user.entity.PersonaElement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommonPointGenerator {

    private final BedrockService bedrockService;
    private final ObjectMapper objectMapper;

    record Extraction(List<Shared> shared, Contrast contrast) {
    }

    record Shared(String content, String keyword, String evidenceA, String evidenceB) {
    }

    record Contrast(String traitA, String traitB) {
    }

    public String generate(List<PersonaElement> myPersona, List<PersonaElement> partnerPersona) {
        Extraction extraction = extract(myPersona, partnerPersona);

        List<String> shared = verifiedShared(extraction, myPersona, partnerPersona);
        if (!shared.isEmpty()) {
            return bedrockService.converse(buildSharedPrompt(shared)).strip();
        }

        Contrast contrast = verifiedContrast(extraction, myPersona, partnerPersona);
        if (contrast != null) {
            return bedrockService.converse(buildContrastPrompt(contrast)).strip();
        }

        WarnLog.log(log, "검증을 통과한 공통점과 대비 특성이 없어 공통점을 생성하지 못했습니다.");
        throw new BusinessException(ErrorCode.AI_RESPONSE_FAILED);
    }

    private Extraction extract(List<PersonaElement> myPersona, List<PersonaElement> partnerPersona) {
        String raw = bedrockService.converse(buildExtractionPrompt(myPersona, partnerPersona));

        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end < start) {
            WarnLog.log(log, "공통점 추출 응답에 JSON 객체가 없습니다.");
            throw new BusinessException(ErrorCode.AI_RESPONSE_FAILED);
        }

        try {
            return objectMapper.readValue(raw.substring(start, end + 1), Extraction.class);
        } catch (JacksonException e) {
            WarnLog.log(log, "공통점 추출 응답을 JSON으로 파싱하지 못했습니다.", e);
            throw new BusinessException(ErrorCode.AI_RESPONSE_FAILED, e);
        }
    }

    private List<String> verifiedShared(Extraction extraction, List<PersonaElement> myPersona, List<PersonaElement> partnerPersona) {
        List<String> result = new ArrayList<>();
        if (extraction.shared() == null) {
            return result;
        }

        for (Shared item : extraction.shared()) {
            if (item.content() == null || item.content().isBlank()) {
                continue;
            }
            if (!isGrounded(myPersona, item.evidenceA()) || !isGrounded(partnerPersona, item.evidenceB())) {
                WarnLog.log(log, "근거가 원문에 없는 공통점을 제외합니다.", field("content", item.content()));
                continue;
            }
            if (!isSharedKeyword(item.keyword(), item.evidenceA(), item.evidenceB())) {
                WarnLog.log(log, "양쪽 근거에 공통 단어가 없는 공통점을 제외합니다.", field("content", item.content()), field("keyword", item.keyword()));
                continue;
            }
            result.add(item.content().strip());
        }

        return result;
    }

    private Contrast verifiedContrast(Extraction extraction, List<PersonaElement> myPersona, List<PersonaElement> partnerPersona) {
        Contrast contrast = extraction.contrast();
        if (contrast == null) {
            return null;
        }
        if (!isGrounded(traitsOf(myPersona), contrast.traitA()) || !isGrounded(traitsOf(partnerPersona), contrast.traitB())) {
            WarnLog.log(log, "근거가 원문에 없는 대비 특성을 제외합니다.");
            return null;
        }

        return new Contrast(contrast.traitA().strip(), contrast.traitB().strip());
    }

    private List<PersonaElement> traitsOf(List<PersonaElement> persona) {
        return persona.stream()
                .filter(element -> element.getDimension() != PersonaDimension.INTEREST
                        && element.getDimension() != PersonaDimension.DETAIL)
                .toList();
    }

    private boolean isSharedKeyword(String keyword, String evidenceA, String evidenceB) {
        if (keyword == null || keyword.strip().length() < 2) {
            return false;
        }

        String stripped = keyword.strip();
        return evidenceA.contains(stripped) && evidenceB.contains(stripped);
    }

    private String answerOf(String explanation) {
        int separator = explanation.indexOf(": ");
        return separator < 0 ? explanation : explanation.substring(separator + 2);
    }

    private boolean isGrounded(List<PersonaElement> persona, String evidence) {
        if (evidence == null || evidence.isBlank()) {
            return false;
        }

        String stripped = evidence.strip();
        return persona.stream()
                .map(PersonaElement::getExplanation)
                .anyMatch(explanation -> explanation.contains(stripped) || stripped.contains(explanation));
    }

    private String buildExtractionPrompt(List<PersonaElement> myPersona, List<PersonaElement> partnerPersona) {
        StringBuilder sb = new StringBuilder();
        sb.append("당신은 두 사람의 정보를 비교해 실제로 겹치는 내용만 골라내는 분석가입니다.\n");
        sb.append("아래는 두 사람 각각에 대해 파악한 정보입니다.\n");

        appendPersona(sb, "A", myPersona);
        appendPersona(sb, "B", partnerPersona);

        sb.append("\n두 사람 모두에게 실제로 있는 공통점을 모두 찾아 JSON으로만 답하세요.\n");
        sb.append("형식: {\"shared\":[{\"content\":\"...\",\"keyword\":\"...\",\"evidenceA\":\"...\",\"evidenceB\":\"...\"}],\"contrast\":{\"traitA\":\"...\",\"traitB\":\"...\"}}\n");
        sb.append("- content: 두 사람이 공유하는 내용을 한 구절로 쓰세요. 세부 내용은 양쪽에 모두 있는 수준까지만 쓰고, 한쪽에만 있는 세부는 넣지 마세요. (예: 공포 영화를 좋아함, 김애란 소설을 읽음, 혼자 있는 시간에 에너지를 회복함)\n");
        sb.append("- content는 evidenceA만 봐도 참이고 evidenceB만 봐도 참이어야 합니다. 한쪽 근거로는 참이 아닌 표현이면 양쪽 모두에 맞는 넓은 표현으로 바꾸세요.\n");
        sb.append("- evidenceA, evidenceB: 그 공통점의 근거가 되는 A와 B의 항목을 각각 위 정보에서 한 글자도 바꾸지 말고 그대로 복사하세요. 성격 특성은 이름 뒤의 설명만 복사하세요.\n");
        sb.append("- keyword: 그 공통점을 나타내면서 evidenceA와 evidenceB 양쪽에 똑같이 들어 있는 단어 하나. 두 글자 이상이어야 합니다. 양쪽에 똑같이 들어 있는 단어가 없으면 그 항목은 공통점이 아니므로 넣지 마세요.\n");
        sb.append("- 양쪽 근거를 댈 수 없는 내용은 shared에 넣지 마세요. 서로 다른 사실을 묶어 만든 공통점도 넣지 마세요. 공통점이 없으면 shared는 빈 배열입니다.\n");
        sb.append("- content는 양쪽 근거에 실제로 있는 사실만 담고, 근거보다 넓게 일반화하지 마세요. (예: 실내 클라이밍과 낚시를 \"야외 활동\"으로 묶지 마세요)\n");
        sb.append("- [나눈 대화]의 공통점을 먼저, 그다음 [관심사], [성격 특성] 순서로 찾으세요.\n");
        sb.append("- contrast: [성격 특성]에서 서로 가장 반대되는 항목을 A와 B에서 하나씩 골라 설명을 그대로 복사하세요. 반대되는 것이 없으면 각자 가장 두드러진 항목을 고르세요.\n");
        sb.append("- JSON 외에 다른 설명이나 코드 블록 표시는 붙이지 마세요.\n");

        return sb.toString();
    }

    private String buildSharedPrompt(List<String> shared) {
        StringBuilder sb = new StringBuilder();
        sb.append("당신은 두 사람을 함께 소개하는 작가입니다.\n");
        sb.append("아래는 두 사람이 실제로 공유하는 공통점 목록입니다. 이 목록에 있는 내용만 사용하고, 목록에 없는 내용은 추측해서 덧붙이지 마세요.\n");

        sb.append("\n[공통점]\n");
        shared.forEach(item -> sb.append("- ").append(item).append("\n"));

        sb.append("\n위 공통점을 바탕으로 두 사람을 함께 소개하듯 한국어로 설명하세요.\n");
        sb.append("- \"두 사람은\"으로 시작하고, 문장은 \"~해요\"로 끝내세요.\n");
        sb.append("- 모든 문장의 주어는 두 사람 전체여야 합니다. A, B, 한 사람, 다른 사람, 한쪽, 다른 쪽처럼 두 사람을 나누어 말하지 마세요.\n");
        sb.append("- \"다르다\", \"다른\", \"차이\", \"매력\"이라는 말과 \"~해 보세요\" 같은 행동 제안은 쓰지 마세요.\n");
        sb.append("- 공통점이 적으면 한두 문장으로 짧게 쓰세요. 한 문단, 4문장 이내, 제목·따옴표·줄바꿈·목록 기호 없이 쓰세요.\n");
        sb.append("- 예시: 두 사람은 영화 보는 걸 좋아하고, 그 중에서도 공포 영화를 제일 좋아해요. 쉬는 날엔 공원 산책을 자주 하며, 잠깐 멈춰서 꽃 구경할 때 행복해해요.\n");

        return sb.toString();
    }

    private String buildContrastPrompt(Contrast contrast) {
        StringBuilder sb = new StringBuilder();
        sb.append("당신은 두 사람을 함께 소개하는 작가입니다.\n");
        sb.append("두 사람은 겹치는 관심사나 경험이 없고, 아래처럼 각자의 성격을 하나씩 갖고 있습니다.\n");

        sb.append("\n[성격 1]\n").append(contrast.traitA()).append("\n");
        sb.append("\n[성격 2]\n").append(contrast.traitB()).append("\n");

        sb.append("\n위 두 성격만 사용해 두 문장으로 쓰세요.\n");
        sb.append("- 첫 문장: \"~하는 사람과 ~하는 사람이 만났어요\" 형태로 쓰세요. 성격은 \"힘이 나는\"처럼 동사로 풀고, 누가 어느 쪽인지 밝히지 마세요.\n");
        sb.append("- 두 성격의 의미를 바꾸거나 서로 반대되게 만들지 말고, 주어진 내용 그대로 풀어 쓰세요.\n");
        sb.append("- 둘째 문장: 주어를 \"두 사람은\" 또는 \"서로\"로 하고, 함께 있으면 서로를 어떻게 채워주는지 \"~할 수 있어요\"처럼 가능성으로 쓰세요. \"운명\", \"천생연분\" 같은 단정은 쓰지 마세요.\n");
        sb.append("- \"다르다\", \"다른\", \"차이\", \"매력\"이라는 말, A, B, 한 사람, 다른 사람, 한쪽, 다른 쪽 같은 표현, \"~해 보세요\" 같은 행동 제안은 쓰지 마세요.\n");
        sb.append("- 한 문단, 제목·따옴표·줄바꿈·목록 기호 없이 쓰세요.\n");
        sb.append("- 예시: 혼자 있을 때 힘이 나는 사람과 사람들 속에서 힘이 나는 사람이 만났어요. 그래서 함께 있으면 서로가 몰랐던 시간을 나눠 가지며 각자의 하루가 넓어지는 관계예요.\n");

        return sb.toString();
    }

    private void appendPersona(StringBuilder sb, String label, List<PersonaElement> personaElements) {
        sb.append("\n[").append(label).append("의 관심사]\n");
        personaElements.stream()
                .filter(element -> element.getDimension() == PersonaDimension.INTEREST)
                .forEach(element -> sb.append("- ").append(element.getExplanation()).append("\n"));

        sb.append("\n[").append(label).append("의 성격 특성]\n");
        personaElements.stream()
                .filter(element -> element.getDimension() != PersonaDimension.INTEREST
                        && element.getDimension() != PersonaDimension.DETAIL)
                .forEach(element -> sb.append("- ").append(element.getDimension()).append(": ").append(element.getExplanation()).append("\n"));

        sb.append("\n[").append(label).append("의 나눈 대화]\n");
        personaElements.stream()
                .filter(element -> element.getDimension() == PersonaDimension.DETAIL)
                .forEach(element -> sb.append("- ").append(answerOf(element.getExplanation())).append("\n"));
    }
}
