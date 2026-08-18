package com.nidus.twinly.common.scene;

import com.nidus.twinly.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SceneNameRendererUnitTest {

    private static final OffsetDateTime OCCURS_AT = OffsetDateTime.of(2026, 7, 26, 12, 0, 0, 0, ZoneOffset.ofHours(9));

    private final SceneNameRenderer renderer = new SceneNameRenderer();

    @Test
    @DisplayName("이름 자리를 유저 이름으로 바꾸고 나머지 문장은 그대로 둔다")
    void placeholders_are_replaced_with_names() {
        // given: 한 문장에 서로 다른 유저의 이름 자리가 두 번 나온다
        String text = "{user_100}이 {user_200}에게 말을 걸었다";

        // when
        String rendered = renderer.render(text, Map.of(100L, "길동", 200L, "철수"));

        // then
        assertThat(rendered).isEqualTo("길동이 철수에게 말을 걸었다");
    }

    @Test
    @DisplayName("이름 자리가 없거나 텍스트가 없으면 원본을 그대로 돌려준다")
    void text_without_placeholder_is_kept() {
        // given: mind처럼 저장되지 않을 수 있는 값
        String mind = null;

        // when & then
        assertThat(renderer.render("복도를 천천히 걸었다", Map.of())).isEqualTo("복도를 천천히 걸었다");
        assertThat(renderer.render(mind, Map.of())).isNull();
    }

    @Test
    @DisplayName("이름을 찾지 못한 자리는 자리표시자 대신 탈퇴한 사용자로 채운다")
    void unknown_user_id_falls_back_to_withdrawn_name() {
        // given: 이름을 알 수 없는 유저의 자리
        String text = "{user_999}이 손을 흔들었다";

        // when: 화면에 자리표시자가 그대로 나가는 것이 가장 나쁘므로 대체 이름을 쓴다
        String rendered = renderer.render(text, Map.of(100L, "길동"));

        // then
        assertThat(rendered).isEqualTo(User.WITHDRAWN_NAME + "이 손을 흔들었다");
    }

    @Test
    @DisplayName("말풍선 줄은 행동과 대사 모두 치환한다")
    void bubble_line_action_and_text_are_replaced() {
        // given
        SceneBubbleLine line = new SceneBubbleLine("bubble", 100L, "{user_200}을 보며", "{user_200}아 안녕", OCCURS_AT);

        // when
        SceneBubbleLine rendered = (SceneBubbleLine) renderer.render(line, Map.of(200L, "철수"));

        // then: 나머지 필드는 건드리지 않는다
        assertThat(rendered.action()).isEqualTo("철수을 보며");
        assertThat(rendered.text()).isEqualTo("철수아 안녕");
        assertThat(rendered.userId()).isEqualTo(100L);
        assertThat(rendered.occursAt()).isEqualTo(line.occursAt());
    }

    @Test
    @DisplayName("나레이션 줄도 치환한다")
    void narration_line_text_is_replaced() {
        // given
        SceneNarrationLine line = new SceneNarrationLine("narr", "{user_100}이 교실에 들어왔다", OCCURS_AT);

        // when
        SceneNarrationLine rendered = (SceneNarrationLine) renderer.render(line, Map.of(100L, "길동"));

        // then
        assertThat(rendered.text()).isEqualTo("길동이 교실에 들어왔다");
    }

    @Test
    @DisplayName("텍스트에 나온 이름 자리의 유저 id를 모두 뽑아낸다")
    void user_ids_are_extracted() {
        // given: 같은 유저가 두 번 나오고, 이름 자리가 아닌 중괄호도 섞여 있다
        String text = "{user_100}이 {user_200}에게 {user_100}의 이야기를 했다 {place}";

        // when & then: 이름을 조회할 대상만 뽑는다
        assertThat(renderer.userIds(text)).containsExactlyInAnyOrder(100L, 200L);
        assertThat(renderer.userIds(null)).isEmpty();
    }

    @Test
    @DisplayName("Long 범위를 넘는 유저 id는 조회 대상에서 빼고 대체 이름으로 채운다")
    void out_of_range_user_id_does_not_break_rendering() {
        // given: 잘못 만들어진 자리표시자
        String text = "{user_99999999999999999999}이 웃었다";

        // when & then: 파싱이 깨져 500으로 번지지 않아야 한다
        assertThat(renderer.userIds(text)).isEmpty();
        assertThat(renderer.render(text, Map.of())).isEqualTo(User.WITHDRAWN_NAME + "이 웃었다");
    }
}
