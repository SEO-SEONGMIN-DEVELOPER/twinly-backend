package com.nidus.twinly.user.generator;

import com.nidus.twinly.common.aws.bedrock.BedrockService;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.user.entity.PersonaElement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PersonaSummaryGeneratorUnitTest {

    private static final Long USER_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    BedrockService bedrockService;

    @InjectMocks
    PersonaSummaryGenerator personaSummaryGenerator;

    @Test
    @DisplayName("소속·관심사·성격·지난 대화를 담은 프롬프트로 요약을 받아 앞뒤 공백을 정리해 반환한다")
    void generate_returns_stripped_summary() {
        // given: 관심사·성격·지난 대화 요소가 있고 모델이 앞뒤 공백이 섞인 요약을 반환
        List<PersonaElement> elements = List.of(
                PersonaElement.create(USER_ID, PersonaDimension.INTEREST, "등산", NOW),
                PersonaElement.create(USER_ID, PersonaDimension.EXTRAVERSION, "사람 만나는 것을 좋아함", NOW),
                PersonaElement.create(USER_ID, PersonaDimension.DETAIL, "등산은 어디로 자주 가?: 북한산", NOW));
        given(bedrockService.converse(anyString())).willReturn("  주말마다 북한산에 오르며 사진으로 순간을 남기는 사람\n");

        // when
        String summary = personaSummaryGenerator.generate("트윈리대학교", elements);

        // then: 공백이 정리된 요약을 반환
        assertThat(summary).isEqualTo("주말마다 북한산에 오르며 사진으로 순간을 남기는 사람");

        // then: 프롬프트에 소속·관심사·성격·지난 대화가 모두 담기고 "~한 사람" 형식을 지시함
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        then(bedrockService).should().converse(promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("트윈리대학교", "[관심사]", "등산", "[성격 특성]", "사람 만나는 것을 좋아함",
                        "[나눈 대화]", "등산은 어디로 자주 가?: 북한산", "~한 사람");
    }

    @Test
    @DisplayName("소속이 없으면 소속 줄을 빼고, 이미 있는 SUMMARY 요소는 성격 특성에 넣지 않는다")
    void generate_without_affiliation_excludes_affiliation_and_summary() {
        // given: 소속이 없고 이전 요약이 요소에 섞여 있음
        List<PersonaElement> elements = List.of(
                PersonaElement.create(USER_ID, PersonaDimension.EXTRAVERSION, "사람 만나는 것을 좋아함", NOW),
                PersonaElement.create(USER_ID, PersonaDimension.SUMMARY, "이전에 만든 요약", NOW));
        given(bedrockService.converse(anyString())).willReturn("사람 만나는 것을 즐기는 사람");

        // when
        personaSummaryGenerator.generate(null, elements);

        // then
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        then(bedrockService).should().converse(promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("[소속 정보]", "사람 만나는 것을 좋아함")
                .doesNotContain("- 소속:", "이전에 만든 요약");
    }
}
