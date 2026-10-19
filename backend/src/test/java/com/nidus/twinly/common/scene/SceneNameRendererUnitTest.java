package com.nidus.twinly.common.scene;

import com.nidus.twinly.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
        assertThat(rendered).isEqualTo(User.WITHDRAWN_NAME + "가 손을 흔들었다");
    }

    @Test
    @DisplayName("말풍선 줄은 행동과 대사 모두 치환한다")
    void bubble_line_action_and_text_are_replaced() {
        // given
        SceneBubbleLine line = new SceneBubbleLine("bubble", 100L, "{user_200}을 보며", "{user_200}아 안녕", OCCURS_AT);

        // when
        SceneBubbleLine rendered = (SceneBubbleLine) renderer.render(line, Map.of(200L, "철수"));

        // then: 나머지 필드는 건드리지 않는다
        assertThat(rendered.action()).isEqualTo("철수를 보며");
        assertThat(rendered.text()).isEqualTo("철수야 안녕");
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
        assertThat(renderer.render(text, Map.of())).isEqualTo(User.WITHDRAWN_NAME + "가 웃었다");
    }

    @ParameterizedTest(name = "{0} + \"{1}\" → {2}")
    @DisplayName("이름 바로 뒤 조사를 받침에 맞게 고른다")
    @CsvSource({
            "서준, 가, 서준이",
            "미미, 이, 미미가",
            "서준, 는, 서준은",
            "미미, 은, 미미는",
            "서준, 를, 서준을",
            "미미, 을, 미미를",
            "서준, 와, 서준과",
            "미미, 과, 미미와",
            "서준, 야, 서준아",
            "미미, 아, 미미야",
            "서준, 랑, 서준이랑",
            "미미, 이랑, 미미랑",
            "서준, 예요, 서준이에요",
            "미미, 이에요, 미미예요",
            "서준, 로, 서준으로",
            "미미, 으로, 미미로",
            "하늘, 으로, 하늘로",
            "하늘, 가, 하늘이",
            "서준, 이야, 서준이야",
            "서준, 였다, 서준이었다",
            "미미, 이었다, 미미였다",
            "서준, 라고, 서준이라고",
            "미미, 이라서, 미미라서",
            "서준, 라도, 서준이라도",
            "미미, 이라는, 미미라는",
            "서준, 란, 서준이란",
            "미미, 이란, 미미란",
            "서준, 나, 서준이나",
            "미미, 이나, 미미나",
            "서준, 며, 서준이며",
            "미미, 이며, 미미며",
            "서준, 고, 서준이고",
            "미미, 이고, 미미고",
            "서준, 면, 서준이면",
            "미미, 이면, 미미면",
            "서준, 지만, 서준이지만",
            "미미, 이지만, 미미지만",
            "서준, 든지, 서준이든지",
            "미미, 이든, 미미든",
            "미미, 이야, 미미야",
            "하늘, 이라고, 하늘이라고"
    })
    void particle_follows_final_consonant_of_name(String name, String written, String expected) {
        // given: AI 는 그 당시 이름의 받침에 맞춰 조사를 붙여 두었다
        String text = "{user_100}" + written + " 왔다";

        // when
        String rendered = renderer.render(text, Map.of(100L, name));

        // then
        assertThat(rendered).isEqualTo(expected + " 왔다");
    }

    @ParameterizedTest(name = "{0} + \"{1}\" → {2}")
    @DisplayName("숫자·영문으로 끝나는 닉네임은 읽는 소리의 받침으로 조사를 고른다")
    @CsvSource({
            "건239, 이, 건239가",
            "은서370, 가, 은서370이",
            "민하1, 로, 민하1로",
            "민하6, 로, 민하6으로",
            "xwy, 이, xwy가",
            "Jun, 가, Jun이",
            "paul, 으로, paul로"
    })
    void particle_follows_reading_of_digits_and_latin(String name, String written, String expected) {
        // when
        String rendered = renderer.render("{user_100}" + written + " 왔다", Map.of(100L, name));

        // then
        assertThat(rendered).isEqualTo(expected + " 왔다");
    }

    @ParameterizedTest(name = "{0} + \"{1}\" → 그대로")
    @DisplayName("받침을 알 수 없는 글자로 끝나는 이름은 AI 가 쓴 조사를 그대로 둔다")
    @CsvSource({
            "별★, 가",
            "오OO, 이",
            "임OO, 과",
            "BTS, 가"
    })
    void unknown_final_keeps_written_particle(String name, String written) {
        // given: 대문자는 낱자로 읽을지(BTS) 기호로 읽을지(쇼케이스 가명 OO) 알 수 없다

        // when
        String rendered = renderer.render("{user_100}" + written + " 왔다", Map.of(100L, name));

        // then
        assertThat(rendered).isEqualTo(name + written + " 왔다");
    }

    @Test
    @DisplayName("조사가 아닌 글자는 바꾸지 않는다")
    void non_particle_text_is_kept() {
        // given: 이름 뒤 띄어쓰기, 다른 조사, 이름에 붙는 '이' 뒤 한글
        Map<Long, String> names = Map.of(100L, "미미");

        // when & then
        assertThat(renderer.render("{user_100} 쪽으로", names)).isEqualTo("미미 쪽으로");
        assertThat(renderer.render("{user_100}의 우동", names)).isEqualTo("미미의 우동");
        assertThat(renderer.render("{user_100} 씨는요?", names)).isEqualTo("미미 씨는요?");
        assertThat(renderer.render("{user_100}이가 웃었다", names)).isEqualTo("미미이가 웃었다");
    }
}
