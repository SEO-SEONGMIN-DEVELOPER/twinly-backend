package com.nidus.twinly.chat.generator;

import com.nidus.twinly.common.aws.bedrock.BedrockService;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.user.entity.PersonaElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class CommonPointGeneratorUnitTest {

    @Mock
    BedrockService bedrockService;

    CommonPointGenerator generator;

    List<PersonaElement> me;
    List<PersonaElement> partner;

    @BeforeEach
    void setUp() {
        generator = new CommonPointGenerator(bedrockService, new ObjectMapper());
        me = List.of(
                personaElement(PersonaDimension.INTEREST, "독서"),
                personaElement(PersonaDimension.EXTRAVERSION, "혼자 있는 시간에 에너지를 회복한다"),
                personaElement(PersonaDimension.LIFE_STYLE, "주말 아침마다 공원을 산책한다"),
                personaElement(PersonaDimension.DETAIL, "요즘 읽는 책은?: 김애란 단편집, 세 번째 읽는 중"),
                personaElement(PersonaDimension.SUMMARY, "김애란 단편집을 세 번째 읽으며 혼자만의 시간을 아끼는 사람")
        );
        partner = List.of(
                personaElement(PersonaDimension.INTEREST, "등산"),
                personaElement(PersonaDimension.EXTRAVERSION, "사람 많은 자리에서 에너지를 얻는다"),
                personaElement(PersonaDimension.LIFE_STYLE, "퇴근 후에 공원을 산책한다"),
                personaElement(PersonaDimension.DETAIL, "요즘 읽는 책은?: 김애란 소설, 문장이 좋아서 계속 읽어")
        );
    }

    @Test
    @DisplayName("추출 응답의 공통점이 양쪽 원문에 근거하면 그 내용만으로 작성 프롬프트를 만들어 두 번째 응답을 반환한다")
    void generate_writes_from_verified_shared_points() {
        // given: 추출 응답은 코드 블록에 감싸여 오고, 근거는 양쪽 원문을 그대로 복사한 상태
        given(bedrockService.converse(any())).willReturn(
                """
                ```json
                {"shared":[{"content":"김애란 소설을 읽음","keyword":"김애란","evidenceA":"김애란 단편집, 세 번째 읽는 중","evidenceB":"김애란 소설, 문장이 좋아서 계속 읽어"}],
                 "contrast":{"traitA":"혼자 있는 시간에 에너지를 회복한다","traitB":"사람 많은 자리에서 에너지를 얻는다"}}
                ```
                """,
                "  두 사람은 김애란 소설을 읽어요.\n"
        );

        // when: 생성
        String result = generator.generate(me, partner);

        // then: 두 번째 응답이 공백 정리되어 반환됨
        assertThat(result).isEqualTo("두 사람은 김애란 소설을 읽어요.");

        // then: 추출 프롬프트에는 두 사람의 원문이, 작성 프롬프트에는 검증된 공통점만 실리고 원문은 실리지 않음
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        then(bedrockService).should(times(2)).converse(captor.capture());
        String extractionPrompt = captor.getAllValues().get(0);
        String writingPrompt = captor.getAllValues().get(1);
        assertThat(extractionPrompt)
                .contains("[A의 관심사]", "독서", "[B의 나눈 대화]", "- 김애란 소설, 문장이 좋아서 계속 읽어", "JSON")
                .doesNotContain("요즘 읽는 책은?");
        assertThat(writingPrompt)
                .contains("[공통점]", "- 김애란 소설을 읽음", "\"두 사람은\"으로 시작")
                .doesNotContain("세 번째 읽는 중", "문장이 좋아서", "혼자 있는 시간", "만났어요");
    }

    @Test
    @DisplayName("관심사나 대화에 근거한 공통점이 있으면 성격 특성에 근거한 공통점은 순서가 앞서도 빼고 작성 프롬프트를 만든다")
    void generate_prefers_shared_points_from_interest_and_dialogue() {
        // given: 생활 방식에 근거한 공통점이 앞에, 대화 답변에 근거한 공통점이 뒤에 오고 둘 다 검증을 통과함
        given(bedrockService.converse(any())).willReturn(
                """
                {"shared":[{"content":"공원을 산책함","keyword":"공원","evidenceA":"주말 아침마다 공원을 산책한다","evidenceB":"퇴근 후에 공원을 산책한다"},
                           {"content":"김애란 소설을 읽음","keyword":"김애란","evidenceA":"김애란 단편집, 세 번째 읽는 중","evidenceB":"김애란 소설, 문장이 좋아서 계속 읽어"}],
                 "contrast":{"traitA":"혼자 있는 시간에 에너지를 회복한다","traitB":"사람 많은 자리에서 에너지를 얻는다"}}
                """,
                "두 사람은 김애란 소설을 읽어요."
        );

        // when: 생성
        generator.generate(me, partner);

        // then: 작성 프롬프트에는 대화 답변에 근거한 공통점만 실림
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        then(bedrockService).should(times(2)).converse(captor.capture());
        assertThat(captor.getAllValues().get(1))
                .contains("[공통점]", "- 김애란 소설을 읽음")
                .doesNotContain("공원을 산책함");
    }

    @Test
    @DisplayName("관심사나 대화에 근거한 공통점이 없으면 대비 성격보다 성격 특성에 근거한 공통점으로 작성 프롬프트를 만든다")
    void generate_falls_back_to_shared_points_from_traits() {
        // given: 검증을 통과한 공통점은 생활 방식에 근거한 것 하나뿐이고, 대비 성격도 양쪽 원문 그대로임
        given(bedrockService.converse(any())).willReturn(
                """
                {"shared":[{"content":"공원을 산책함","keyword":"공원","evidenceA":"주말 아침마다 공원을 산책한다","evidenceB":"퇴근 후에 공원을 산책한다"}],
                 "contrast":{"traitA":"혼자 있는 시간에 에너지를 회복한다","traitB":"사람 많은 자리에서 에너지를 얻는다"}}
                """,
                "두 사람은 공원을 산책해요."
        );

        // when: 생성
        String result = generator.generate(me, partner);

        // then: 공통점 형식 응답이 반환되고, 작성 프롬프트에는 성격 특성 공통점이 실리며 대비 성격은 실리지 않음
        assertThat(result).isEqualTo("두 사람은 공원을 산책해요.");
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        then(bedrockService).should(times(2)).converse(captor.capture());
        assertThat(captor.getAllValues().get(1))
                .contains("[공통점]", "- 공원을 산책함")
                .doesNotContain("[성격 1]", "만났어요");
    }

    @Test
    @DisplayName("검증을 통과한 공통점이 세 개를 넘으면 앞에서부터 세 개만 작성 프롬프트에 싣는다")
    void generate_limits_shared_points_to_three() {
        // given: 양쪽 대화 답변에 근거한 공통점이 네 개 검증을 통과함
        List<PersonaElement> myDetails = List.of(
                personaElement(PersonaDimension.DETAIL, "주말엔 뭐 해?: 영화관에 가서 조조 영화를 봐"),
                personaElement(PersonaDimension.DETAIL, "좋아하는 음식은?: 매운 떡볶이"),
                personaElement(PersonaDimension.DETAIL, "가 보고 싶은 곳은?: 제주도 바다"),
                personaElement(PersonaDimension.DETAIL, "요즘 듣는 노래는?: 아이유 신곡")
        );
        List<PersonaElement> partnerDetails = List.of(
                personaElement(PersonaDimension.DETAIL, "주말엔 뭐 해?: 영화관에서 팝콘 먹으며 영화 봐"),
                personaElement(PersonaDimension.DETAIL, "좋아하는 음식은?: 떡볶이 맛집 탐방"),
                personaElement(PersonaDimension.DETAIL, "가 보고 싶은 곳은?: 제주도 한라산"),
                personaElement(PersonaDimension.DETAIL, "요즘 듣는 노래는?: 아이유 콘서트 실황")
        );
        given(bedrockService.converse(any())).willReturn(
                """
                {"shared":[{"content":"영화관에서 영화를 봄","keyword":"영화관","evidenceA":"영화관에 가서 조조 영화를 봐","evidenceB":"영화관에서 팝콘 먹으며 영화 봐"},
                           {"content":"떡볶이를 좋아함","keyword":"떡볶이","evidenceA":"매운 떡볶이","evidenceB":"떡볶이 맛집 탐방"},
                           {"content":"제주도에 가고 싶어함","keyword":"제주도","evidenceA":"제주도 바다","evidenceB":"제주도 한라산"},
                           {"content":"아이유 노래를 들음","keyword":"아이유","evidenceA":"아이유 신곡","evidenceB":"아이유 콘서트 실황"}],
                 "contrast":null}
                """,
                "두 사람은 영화관에서 영화를 보고 떡볶이를 좋아해요."
        );

        // when: 생성
        generator.generate(myDetails, partnerDetails);

        // then: 작성 프롬프트에는 앞의 세 공통점만 실림
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        then(bedrockService).should(times(2)).converse(captor.capture());
        assertThat(captor.getAllValues().get(1))
                .contains("- 영화관에서 영화를 봄", "- 떡볶이를 좋아함", "- 제주도에 가고 싶어함")
                .doesNotContain("아이유 노래를 들음");
    }

    @Test
    @DisplayName("근거가 원문에 없거나 양쪽 근거에 공통 단어가 없는 공통점은 제외하고, 남는 공통점이 없으면 대비 성격으로 만남 프롬프트를 만든다")
    void generate_drops_ungrounded_shared_and_falls_back_to_contrast() {
        // given: 첫 항목은 B 근거가 원문에 없고, 둘째 항목은 근거는 양쪽 원문이지만 서로 다른 특성을 묶어 공통 단어가 없음
        //        대비 성격은 양쪽 원문 그대로
        given(bedrockService.converse(any())).willReturn(
                """
                {"shared":[{"content":"산을 좋아함","keyword":"산","evidenceA":"독서","evidenceB":"주말마다 북한산에 간다"},
                           {"content":"혼자 또는 소수와 깊게 어울림","keyword":"혼자","evidenceA":"혼자 있는 시간에 에너지를 회복한다","evidenceB":"사람 많은 자리에서 에너지를 얻는다"}],
                 "contrast":{"traitA":"혼자 있는 시간에 에너지를 회복한다","traitB":"사람 많은 자리에서 에너지를 얻는다"}}
                """,
                "혼자 있을 때 힘이 나는 사람과 사람들 속에서 힘이 나는 사람이 만났어요."
        );

        // when: 생성
        String result = generator.generate(me, partner);

        // then: 만남 형식 응답이 반환되고, 작성 프롬프트에는 두 성격만 실리며 제외된 공통점은 실리지 않음
        assertThat(result).startsWith("혼자 있을 때 힘이 나는 사람과");
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        then(bedrockService).should(times(2)).converse(captor.capture());
        String writingPrompt = captor.getAllValues().get(1);
        assertThat(writingPrompt)
                .contains("[성격 1]", "혼자 있는 시간에 에너지를 회복한다", "[성격 2]", "사람 많은 자리에서 에너지를 얻는다", "만났어요")
                .doesNotContain("산을 좋아함", "혼자 또는 소수와", "[공통점]");
    }

    @Test
    @DisplayName("공통점이 비어 있고 대비 성격이 성격 특성이 아닌 대화 답변에서 나오면 작성 호출 없이 AI_RESPONSE_FAILED 예외가 발생한다")
    void generate_throws_when_nothing_is_grounded() {
        // given: 공통점은 비어 있고, 대비 성격의 A 근거는 원문에 있지만 성격 특성이 아니라 대화 답변임
        given(bedrockService.converse(any())).willReturn(
                """
                {"shared":[],"contrast":{"traitA":"김애란 단편집, 세 번째 읽는 중","traitB":"사람 많은 자리에서 에너지를 얻는다"}}
                """
        );

        // when & then: 예외 + Bedrock 은 추출 한 번만 호출됨
        assertThatThrownBy(() -> generator.generate(me, partner))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_RESPONSE_FAILED);
        then(bedrockService).should(times(1)).converse(any());
    }

    @Test
    @DisplayName("요약은 추출 프롬프트에 싣지 않고, 대비 성격의 근거로 오면 성격 특성이 아니므로 AI_RESPONSE_FAILED 예외가 발생한다")
    void generate_excludes_summary_from_prompt_and_contrast() {
        // given: 공통점은 비어 있고, 대비 성격의 A 근거가 요약 원문임
        given(bedrockService.converse(any())).willReturn(
                """
                {"shared":[],"contrast":{"traitA":"김애란 단편집을 세 번째 읽으며 혼자만의 시간을 아끼는 사람","traitB":"사람 많은 자리에서 에너지를 얻는다"}}
                """
        );

        // when & then: 예외 + 추출 프롬프트에 요약이 실리지 않음
        assertThatThrownBy(() -> generator.generate(me, partner))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_RESPONSE_FAILED);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        then(bedrockService).should(times(1)).converse(captor.capture());
        assertThat(captor.getValue())
                .contains("[A의 성격 특성]", "혼자 있는 시간에 에너지를 회복한다")
                .doesNotContain("SUMMARY", "혼자만의 시간을 아끼는 사람");
    }

    @Test
    @DisplayName("추출 응답이 JSON 이 아니면 AI_RESPONSE_FAILED 예외가 발생한다")
    void generate_throws_on_non_json_extraction() {
        // given: 모델이 JSON 대신 문장을 반환
        given(bedrockService.converse(any())).willReturn("두 사람은 독서를 좋아해요.");

        // when & then
        assertThatThrownBy(() -> generator.generate(me, partner))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_RESPONSE_FAILED);
        then(bedrockService).should(times(1)).converse(any());
    }

    private PersonaElement personaElement(PersonaDimension dimension, String explanation) {
        return PersonaElement.create(1L, dimension, explanation, Instant.parse("2026-08-01T00:00:00Z"));
    }
}
