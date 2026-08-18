package com.nidus.twinly.common.parallel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParallelStoryRendererUnitTest {

    ParallelStoryRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new ParallelStoryRenderer();
    }

    @Test
    @DisplayName("받침이 있는 이름에는 과, 은, 이가 붙는다")
    void particles_after_a_name_ending_with_a_final_consonant() {
        // given: 지훈은 받침으로 끝난다
        String story = "{A와} {B는} 만났고 {A가} 먼저 말했습니다.";

        // when
        String rendered = renderer.render(story, "지훈", "지훈");

        // then
        assertThat(rendered).isEqualTo("지훈과 지훈은 만났고 지훈이 먼저 말했습니다.");
    }

    @Test
    @DisplayName("받침이 없는 이름에는 와, 는, 가가 그대로 붙는다")
    void particles_after_a_name_ending_without_a_final_consonant() {
        // given: 민서는 모음으로 끝난다
        String story = "{A와} {B는} 만났고 {A가} 먼저 말했습니다.";

        // when
        String rendered = renderer.render(story, "민서", "민서");

        // then
        assertThat(rendered).isEqualTo("민서와 민서는 만났고 민서가 먼저 말했습니다.");
    }

    @Test
    @DisplayName("두 사람의 받침이 서로 달라도 각자에 맞는 조사가 붙는다")
    void particles_are_chosen_per_name() {
        // given
        String story = "{A와} {B는} 다른 우주에서 만났습니다.";

        // when
        String rendered = renderer.render(story, "민서", "지훈");

        // then
        assertThat(rendered).isEqualTo("민서와 지훈은 다른 우주에서 만났습니다.");
    }

    @Test
    @DisplayName("조사 없이 이름만 넣을 수도 있다")
    void placeholder_without_particle() {
        // given
        String story = "그 우주의 {A}, 그리고 {B}.";

        // when
        String rendered = renderer.render(story, "지훈", "민서");

        // then
        assertThat(rendered).isEqualTo("그 우주의 지훈, 그리고 민서.");
    }

    @Test
    @DisplayName("한글이 아닌 이름은 받침이 없는 것으로 본다")
    void non_hangul_name_is_treated_as_open_syllable() {
        // given: 영문 이름이 들어와도 문장이 깨지지 않아야 한다
        String story = "{A와} {B는} 만났습니다.";

        // when
        String rendered = renderer.render(story, "Alex", "민서");

        // then
        assertThat(rendered).isEqualTo("Alex와 민서는 만났습니다.");
    }

    @Test
    @DisplayName("지원하는 이름 자리만 있으면 걸리는 것이 없다")
    void supported_placeholders_are_not_reported() {
        // given: 조사가 붙은 자리와 조사가 없는 자리를 모두 쓴다
        String story = "{A와} {B는} 만났고 {A가} 웃었습니다. 그리고 {A}, {B}.";

        // when & then
        assertThat(renderer.unsupportedPlaceholders(story)).isEmpty();
    }

    @Test
    @DisplayName("채울 수 없는 조사가 붙은 이름 자리를 모두 찾아낸다")
    void unsupported_placeholders_are_all_reported() {
        // given: 의, 한테는 렌더링 대상이 아니라 화면에 그대로 나간다
        String story = "{A의} 부모님이 {B한테} 물었고 {B는} 웃었습니다.";

        // when & then: 한 번에 다 보여줘야 고치고 재기동을 반복하지 않는다
        assertThat(renderer.unsupportedPlaceholders(story)).containsExactly("{A의}", "{B한테}");
    }

    @Test
    @DisplayName("A와 B가 아닌 이름 자리도 걸러낸다")
    void placeholder_with_unknown_name_slot_is_reported() {
        // given: 오타로 C가 들어간 경우
        String story = "{A와} {C는} 만났습니다.";

        // when & then
        assertThat(renderer.unsupportedPlaceholders(story)).containsExactly("{C는}");
    }
}
