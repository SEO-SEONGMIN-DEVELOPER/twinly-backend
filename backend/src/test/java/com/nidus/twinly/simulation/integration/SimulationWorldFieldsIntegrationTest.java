package com.nidus.twinly.simulation.integration;

import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.activity.repository.SimulationHomeObjectRepository;
import com.nidus.twinly.activity.repository.UserRoomRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SimulationWorldFieldsIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 18);
    private static final String INTIMACY_AS_OF = "2026-08-18T06:00:00";
    private static final long CURRENT_SEASON_ID = 1L;
    // 엑스트라 번호는 70자리가 넘을 수 있어 숫자로 읽으면 끝자리가 뭉개진다
    private static final String LONG_EXTRA_REF = "extra:" + "1234567890".repeat(8);

    @Autowired
    SceneRepository sceneRepository;

    @Autowired
    SimulationHomeObjectRepository simulationHomeObjectRepository;

    @Autowired
    UserRoomRepository userRoomRepository;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    @DisplayName("새 칸 6개를 받으면 저장하고 v2 활동 조회에는 그대로, v1 활동 조회에는 내려 주지 않는다")
    void simulations_saves_world_fields_and_exposes_them_only_in_v2() throws Exception {
        // given: 활성 시즌, 집 행동 장면(들은 말·활동·동작)과 대화 장면(직원 서술 줄), 방·집 사물이 담긴 하루 결과
        saveCurrentSeason();
        User me = saveUser();
        User partner = saveUser();
        String scenes = """
                [
                  {
                    "type": "action", "start": "2026-08-18T07:00:00", "end": "2026-08-18T07:20:00",
                    "place": "집", "placeCode": "P0258", "narration": "라면을 끓였다",
                    "activity": "요리", "motion": "cook",
                    "lines": [
                      {"t": "narr", "text": "택배 기사: \\"택배 왔습니다.\\"", "occursAt": "2026-08-18T07:05:00", "speaker": "%s"}
                    ]
                  },
                  {
                    "type": "dialogue", "start": "2026-08-18T13:00:00", "end": "2026-08-18T13:30:00",
                    "place": "카페", "placeCode": "P0103", "with": ["%d"],
                    "lines": [
                      {"t": "narr", "text": "바리스타 지은: \\"라떼 나왔습니다.\\"", "occursAt": "2026-08-18T13:20:05", "speaker": "npc:npc082"},
                      {"t": "bubble", "userId": "%d", "text": "고마워요", "occursAt": "2026-08-18T13:21:00"}
                    ]
                  }
                ]
                """.formatted(LONG_EXTRA_REF, partner.getId(), me.getId());
        String extras = """
                "room": {"frame": "작은 원룸", "items": ["기타"]},
                "homeObjects": [
                  {"objectId": "home-o1", "kind": "방 조명", "initial": "꺼짐",
                   "changes": [{"at": "2026-08-18T07:00:00", "state": "켜짐", "by": "user:%d"}]}
                ]
                """.formatted(me.getId());

        // when: 하루 결과 저장
        simulate(me, scenes, extras).andExpect(status().isOk());
        flushAndClear();

        // then: v2 에는 받은 값이 그대로 나온다 (긴 엑스트라 번호도 글자 그대로)
        mockMvc.perform(get("/api/v2/activities/{date}", "2026-08-18").header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenes", hasSize(2)))
                .andExpect(jsonPath("$.scenes[0].type").value("action"))
                .andExpect(jsonPath("$.scenes[0].activity").value("요리"))
                .andExpect(jsonPath("$.scenes[0].motion").value("cook"))
                .andExpect(jsonPath("$.scenes[0].lines", hasSize(1)))
                .andExpect(jsonPath("$.scenes[0].lines[0].t").value("narr"))
                .andExpect(jsonPath("$.scenes[0].lines[0].speaker").value(LONG_EXTRA_REF))
                .andExpect(jsonPath("$.scenes[1].lines[0].speaker").value("npc:npc082"))
                .andExpect(jsonPath("$.scenes[1].lines[1].t").value("bubble"))
                .andExpect(jsonPath("$.room.frame").value("작은 원룸"))
                .andExpect(jsonPath("$.room.items[0]").value("기타"))
                .andExpect(jsonPath("$.homeObjects[0].objectId").value("home-o1"))
                .andExpect(jsonPath("$.homeObjects[0].changes[0].by").value("user:" + me.getId()));

        // then: v1 은 출시된 앱이 받는 모양 그대로라 새 칸이 하나도 없다
        mockMvc.perform(get("/api/v1/activities/{date}", "2026-08-18").header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenes[0].activity").doesNotExist())
                .andExpect(jsonPath("$.scenes[0].motion").doesNotExist())
                .andExpect(jsonPath("$.scenes[0].lines").doesNotExist())
                .andExpect(jsonPath("$.scenes[1].lines[0].speaker").doesNotExist())
                .andExpect(jsonPath("$.room").doesNotExist())
                .andExpect(jsonPath("$.homeObjects").doesNotExist());
    }

    @Test
    @DisplayName("새 칸이 하나도 없는 요청도 지금처럼 저장되고 v2 에서는 새 칸이 모두 null 이다")
    void simulations_without_world_fields_saves_as_before() throws Exception {
        // given: AI 가 새 칸을 싣기 전 모양의 하루 결과
        saveCurrentSeason();
        User me = saveUser();
        String scenes = """
                [{"type": "action", "start": "2026-08-18T07:00:00", "end": "2026-08-18T07:20:00",
                  "place": "집", "narration": "잠을 잤다"}]
                """;

        // when: 하루 결과 저장
        simulate(me, scenes, null).andExpect(status().isOk());
        flushAndClear();

        // then: 장면은 저장되고, 방·집 사물 행은 만들지 않는다
        assertThat(sceneRepository.findAllByUserIdAndDate(me.getId(), DATE)).singleElement()
                .satisfies(scene -> assertThat(scene.getLines()).isNull());
        assertThat(userRoomRepository.findByUserId(me.getId())).isEmpty();
        assertThat(simulationHomeObjectRepository.findByUserIdAndDate(me.getId(), DATE)).isEmpty();

        // then: v2 에서 새 칸은 모두 null 로 내려간다
        mockMvc.perform(get("/api/v2/activities/{date}", "2026-08-18").header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenes[0].activity").isEmpty())
                .andExpect(jsonPath("$.scenes[0].motion").isEmpty())
                .andExpect(jsonPath("$.scenes[0].lines").isEmpty())
                .andExpect(jsonPath("$.room").isEmpty())
                .andExpect(jsonPath("$.homeObjects").isEmpty());
    }

    @Test
    @DisplayName("행동 장면 lines 가 빈 배열이면 없음과 같게 NULL 로 저장되고 v2 에서 null 이다")
    void simulations_saves_empty_action_lines_as_null() throws Exception {
        // given: lines 를 빈 배열로 보낸 행동 장면
        saveCurrentSeason();
        User me = saveUser();
        String scenes = """
                [{"type": "action", "start": "2026-08-18T07:00:00", "end": "2026-08-18T07:20:00",
                  "place": "집", "narration": "잠을 잤다", "lines": []}]
                """;

        // when: 하루 결과 저장
        simulate(me, scenes, null).andExpect(status().isOk());
        flushAndClear();

        // then: DB 에는 기존 행동 장면과 같은 NULL, 응답에는 null
        assertThat(sceneRepository.findAllByUserIdAndDate(me.getId(), DATE)).extracting(Scene::getLines).containsOnlyNulls();
        mockMvc.perform(get("/api/v2/activities/{date}", "2026-08-18").header("Authorization", bearer(me.getId())))
                .andExpect(jsonPath("$.scenes[0].lines").isEmpty());
    }

    @Test
    @DisplayName("행동 장면 lines 에 bubble 줄이 오면 400 INVALID_REQUEST 를 반환하고 아무것도 저장하지 않는다")
    void simulations_rejects_bubble_line_in_action_scene() throws Exception {
        // given: 서술 줄만 와야 하는 행동 장면에 bubble 줄이 섞인 결과
        User me = saveUser();
        String scenes = """
                [{"type": "action", "start": "2026-08-18T07:00:00", "end": "2026-08-18T07:20:00",
                  "place": "집", "narration": "잠을 잤다",
                  "lines": [{"t": "bubble", "userId": "%d", "text": "안녕", "occursAt": "2026-08-18T07:05:00"}]}]
                """.formatted(me.getId());

        // when & then: 서술 줄로 둔갑해 저장되지 않고 형식 위반으로 거절된다
        simulate(me, scenes, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(sceneRepository.findAllByUserIdAndDate(me.getId(), DATE)).isEmpty();
    }

    @Test
    @DisplayName("room 이 객체가 아니거나 homeObjects 가 배열이 아니면 400 INVALID_REQUEST 를 반환한다")
    void simulations_rejects_wrong_shape_of_room_and_home_objects() throws Exception {
        // given: 장면 하나짜리 결과
        User me = saveUser();
        String scenes = """
                [{"type": "action", "start": "2026-08-18T07:00:00", "end": "2026-08-18T07:20:00",
                  "place": "집", "narration": "잠을 잤다"}]
                """;

        // when & then: room 이 문자열이면 거절
        simulate(me, scenes, "\"room\": \"작은 원룸\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        // when & then: homeObjects 가 객체면 거절
        simulate(me, scenes, "\"homeObjects\": {\"objectId\": \"home-o1\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("activity 가 100자를 넘으면 400 INVALID_REQUEST 를 반환한다")
    void simulations_rejects_too_long_activity() throws Exception {
        // given: activity 가 101자인 행동 장면
        User me = saveUser();
        String scenes = """
                [{"type": "action", "start": "2026-08-18T07:00:00", "end": "2026-08-18T07:20:00",
                  "place": "집", "narration": "잠을 잤다", "activity": "%s"}]
                """.formatted("가".repeat(101));

        // when & then: DB 오류(500)로 하루 전체를 잃지 않고 형식 위반 400 으로 거절된다
        simulate(me, scenes, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("room 은 같거나 더 최근 날짜로 받을 때만 바뀌고, 지난 날짜를 다시 보내도 최신 값이 유지된다")
    void simulations_overwrites_room_only_with_same_or_newer_date() throws Exception {
        // given: 8월 18일에 받은 방
        User me = saveUser();
        simulate(me, DATE, emptyActionScene(DATE), "\"room\": {\"frame\": \"작은 원룸\", \"items\": [\"기타\"]}")
                .andExpect(status().isOk());

        // when: 8월 17일 결과를 다른 방으로 다시 보낸다
        LocalDate previousDate = DATE.minusDays(1);
        simulate(me, previousDate, emptyActionScene(previousDate), "\"room\": {\"frame\": \"큰 투룸\", \"items\": []}")
                .andExpect(status().isOk());
        entityManager.clear();

        // then: 옛 날짜 값이 최신 값을 덮지 않는다
        assertThat(userRoomRepository.findByUserId(me.getId())).hasValueSatisfying(room -> {
            assertThat(room.getRoom()).contains("작은 원룸");
            assertThat(room.getDate()).isEqualTo(DATE);
        });

        // when: 같은 날짜(8월 18일)로 고쳐서 다시 보낸다
        simulate(me, DATE, emptyActionScene(DATE), "\"room\": {\"frame\": \"작은 원룸\", \"items\": [\"기타\", \"화분\"]}")
                .andExpect(status().isOk());
        entityManager.clear();

        // then: 같은 날짜 재전송은 교정으로 받아들인다
        assertThat(userRoomRepository.findByUserId(me.getId())).hasValueSatisfying(room -> {
            assertThat(room.getRoom()).contains("화분");
            assertThat(room.getDate()).isEqualTo(DATE);
        });
    }

    @Test
    @DisplayName("같은 날짜로 다시 보내면 homeObjects 는 통째로 바뀌고, 칸이 빠지면 그날 집 사물 행도 지워진다")
    void simulations_replaces_home_objects_of_same_date() throws Exception {
        // given: 집 사물이 담긴 1차 결과
        User me = saveUser();
        simulate(me, emptyActionScene(DATE), "\"homeObjects\": [{\"objectId\": \"home-o1\", \"kind\": \"창문\", \"initial\": \"닫힘\", \"changes\": []}]")
                .andExpect(status().isOk());

        // when: 같은 날짜로 다른 집 사물을 보낸다
        simulate(me, emptyActionScene(DATE), "\"homeObjects\": [{\"objectId\": \"home-o2\", \"kind\": \"TV\", \"initial\": \"꺼짐\", \"changes\": []}]")
                .andExpect(status().isOk());
        entityManager.clear();

        // then: 한 행만 남고 새 값으로 바뀐다 (유니크 키 위반 없이)
        assertThat(simulationHomeObjectRepository.findByUserIdAndDate(me.getId(), DATE))
                .hasValueSatisfying(homeObject -> assertThat(homeObject.getObjects()).contains("home-o2").doesNotContain("home-o1"));

        // when: 같은 날짜로 homeObjects 없이 다시 보낸다
        simulate(me, emptyActionScene(DATE), null).andExpect(status().isOk());
        entityManager.clear();

        // then: 전체 교체 규칙대로 그날 집 사물 행이 사라진다
        assertThat(simulationHomeObjectRepository.findByUserIdAndDate(me.getId(), DATE)).isEmpty();
    }

    private ResultActions simulate(User me, String scenes, String extraFields) throws Exception {
        return simulate(me, DATE, scenes, extraFields);
    }

    private ResultActions simulate(User me, LocalDate date, String scenes, String extraFields) throws Exception {
        String payload = """
                {
                  "userId": "%d",
                  "date": "%s",
                  "scenes": %s,
                  "questions": [],
                  "relationships": [],
                  "intimacyAsOf": "%s"%s
                }
                """.formatted(me.getId(), date, scenes, INTIMACY_AS_OF, extraFields == null ? "" : ",\n" + extraFields);

        return mockMvc.perform(post("/internal/v1/users/{userId}/simulations", me.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload));
    }

    private String emptyActionScene(LocalDate date) {
        return """
                [{"type": "action", "start": "%sT07:00:00", "end": "%sT07:20:00", "place": "집", "narration": "잠을 잤다"}]
                """.formatted(date, date);
    }

    private void saveCurrentSeason() {
        entityManager.createNativeQuery("""
                        INSERT INTO seasons (id, started_at, ended_at, is_active, created_at)
                        VALUES (:id, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 1, UTC_TIMESTAMP(6))
                        """)
                .setParameter("id", CURRENT_SEASON_ID)
                .executeUpdate();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
