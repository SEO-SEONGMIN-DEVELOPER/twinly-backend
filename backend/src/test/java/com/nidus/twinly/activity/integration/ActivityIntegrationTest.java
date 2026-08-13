package com.nidus.twinly.activity.integration;

import com.nidus.twinly.activity.domain.QuestionType;
import com.nidus.twinly.activity.domain.SceneType;
import com.nidus.twinly.activity.entity.Question;
import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.entity.ScenePartner;
import com.nidus.twinly.activity.repository.QuestionRepository;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActivityIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 26);

    /** 활성 시즌으로 넣을 시즌 id. 응답의 seasonId가 이 값이 된다. */
    private static final long CURRENT_SEASON_ID = 1L;

    @Autowired
    SceneRepository sceneRepository;

    @Autowired
    ScenePartnerRepository scenePartnerRepository;

    @Autowired
    QuestionRepository questionRepository;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    @DisplayName("활동 조회 성공: 실제 유저·JWT 인증·MockMvc·DB까지 관통하여 action 씬과 질문이 KST 시각으로 내려온다")
    void activity_action_scene_and_question_end_to_end() throws Exception {
        // given: 활성 시즌과 실제 유저 2명(조회자/동행자), 그들의 action 씬 1개, 질문 1개를 DB에 저장 (FK 때문에 유저부터 저장)
        saveCurrentSeason();
        User me = saveUser();
        User partner = saveUser();

        Scene scene = sceneRepository.save(actionScene(me.getId(), "v1", "학교 복도",
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                "복도를 천천히 걸었다", "조금 설레었다"));
        scenePartnerRepository.save(scenePartner(scene.getId(), partner.getId()));
        Question question = questionRepository.save(question(me.getId(), LocalTime.of(21, 30),
                QuestionType.PROMISE, "오늘 어땠어?", List.of("좋았어", "별로야")));
        flushAndClear();

        // when: 조회자의 실제 액세스 토큰으로 해당 날짜의 활동 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/activities/{date}", "2026-07-26")
                .header("Authorization", bearer(me.getId())));

        // then: 200 + DB에 저장한 씬/질문이 KST 시각과 동행자 이름까지 채워져 내려온다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(me.getId().toString()))
                .andExpect(jsonPath("$.seasonId").value("1"))
                .andExpect(jsonPath("$.date").value("2026-07-26"))
                .andExpect(jsonPath("$.version").value("v1"))
                .andExpect(jsonPath("$.serverNow").isNotEmpty())
                .andExpect(jsonPath("$.scenes", hasSize(1)))
                .andExpect(jsonPath("$.scenes[0].sceneId").value(scene.getId().toString()))
                .andExpect(jsonPath("$.scenes[0].type").value("action"))
                .andExpect(jsonPath("$.scenes[0].startsAt", startsWith("2026-07-26T09:00")))
                .andExpect(jsonPath("$.scenes[0].endsAt", startsWith("2026-07-26T10:00")))
                .andExpect(jsonPath("$.scenes[0].place").value("학교 복도"))
                .andExpect(jsonPath("$.scenes[0].narration").value("복도를 천천히 걸었다"))
                .andExpect(jsonPath("$.scenes[0].mind").value("조금 설레었다"))
                .andExpect(jsonPath("$.scenes[0].with", hasSize(1)))
                .andExpect(jsonPath("$.scenes[0].with[0]").value(partner.getId().toString()))
                .andExpect(jsonPath("$.userInfos", hasSize(2)))
                .andExpect(jsonPath("$.userInfos[0].userId").value(me.getId().toString()))
                .andExpect(jsonPath("$.userInfos[1].userId").value(partner.getId().toString()))
                .andExpect(jsonPath("$.userInfos[1].userName")
                        .value(partner.getFamilyName() + partner.getGivenName()))
                .andExpect(jsonPath("$.questions", hasSize(1)))
                .andExpect(jsonPath("$.questions[0].id").value(question.getId().toString()))
                .andExpect(jsonPath("$.questions[0].type").value("PROMISE"))
                .andExpect(jsonPath("$.questions[0].time", startsWith("2026-07-26T21:30")))
                .andExpect(jsonPath("$.questions[0].text").value("오늘 어땠어?"))
                .andExpect(jsonPath("$.questions[0].options", hasSize(2)))
                .andExpect(jsonPath("$.questions[0].options[0]").value("좋았어"));
    }

    @Test
    @DisplayName("활동 조회 성공: dialogue 씬의 JSON 컬럼(lines)이 DB에서 읽혀 대사 목록으로 내려온다")
    void activity_dialogue_scene_end_to_end() throws Exception {
        // given: 활성 시즌과 lines JSON을 가진 dialogue 씬 1개를 DB에 저장
        saveCurrentSeason();
        User me = saveUser();
        User partner = saveUser();

        String linesJson = """
                [
                  {"t":"narr","text":"교실이 조용해졌다","occursAt":"12:00:00"},
                  {"t":"bubble","userId":%d,"action":"웃으며","text":"안녕","occursAt":"12:05:00"}
                ]
                """.formatted(partner.getId());

        Scene scene = sceneRepository.save(dialogueScene(me.getId(), "v1", "교실",
                LocalTime.of(12, 0), LocalTime.of(12, 30), linesJson));
        scenePartnerRepository.save(scenePartner(scene.getId(), partner.getId()));
        flushAndClear();

        // when: 조회자의 실제 액세스 토큰으로 해당 날짜의 활동 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/activities/{date}", "2026-07-26")
                .header("Authorization", bearer(me.getId())));

        // then: 200 + JSON 컬럼이 나레이션/말풍선 두 줄로 파싱되어 내려온다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.scenes", hasSize(1)))
                .andExpect(jsonPath("$.scenes[0].type").value("dialogue"))
                .andExpect(jsonPath("$.scenes[0].lines", hasSize(2)))
                .andExpect(jsonPath("$.scenes[0].lines[0].text").value("교실이 조용해졌다"))
                .andExpect(jsonPath("$.scenes[0].lines[0].occursAt", startsWith("2026-07-26T12:00")))
                .andExpect(jsonPath("$.scenes[0].lines[1].userId").value(partner.getId().toString()))
                .andExpect(jsonPath("$.scenes[0].lines[1].action").value("웃으며"))
                .andExpect(jsonPath("$.scenes[0].lines[1].text").value("안녕"))
                .andExpect(jsonPath("$.scenes[0].lines[1].occursAt", startsWith("2026-07-26T12:05")));
    }

    @Test
    @DisplayName("해당 날짜에 데이터가 없으면 200과 함께 빈 목록과 null version을 반환한다")
    void activity_without_data_returns_empty_lists() throws Exception {
        // given: 활성 시즌만 있고 씬도 질문도 저장하지 않은 실제 유저
        saveCurrentSeason();
        User me = saveUser();

        // when: 조회자의 실제 액세스 토큰으로 데이터가 없는 날짜의 활동 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/activities/{date}", "2026-07-26")
                .header("Authorization", bearer(me.getId())));

        // then: 200 + version은 null, scenes/questions는 빈 배열이고 userInfos에는 조회자 본인만 남는다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(me.getId().toString()))
                .andExpect(jsonPath("$.date").value("2026-07-26"))
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.scenes").isEmpty())
                .andExpect(jsonPath("$.questions").isEmpty())
                .andExpect(jsonPath("$.userInfos", hasSize(1)))
                .andExpect(jsonPath("$.userInfos[0].userId").value(me.getId().toString()));
    }

    /** seasons는 엔티티에 생성 팩토리가 없어 네이티브 INSERT로 넣고, 현재 시즌이 되도록 is_active를 켠다. */
    private void saveCurrentSeason() {
        entityManager.createNativeQuery("""
                        INSERT INTO seasons (id, started_at, ended_at, is_active, created_at)
                        VALUES (:id, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 1, UTC_TIMESTAMP(6))
                        """)
                .setParameter("id", CURRENT_SEASON_ID)
                .executeUpdate();
    }

    /** 영속성 컨텍스트 캐시가 아니라 실제 MySQL에서 다시 읽어오도록 강제한다 (JSON 컬럼 왕복 검증). */
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private Scene actionScene(Long userId, String version, String place,
                              LocalTime startsAt, LocalTime endsAt,
                              String narration, String mind) {
        Scene scene = newScene(userId, version, place, startsAt, endsAt, SceneType.ACTION);
        ReflectionTestUtils.setField(scene, "narration", narration);
        ReflectionTestUtils.setField(scene, "mind", mind);
        return scene;
    }

    private Scene dialogueScene(Long userId, String version, String place,
                                LocalTime startsAt, LocalTime endsAt, String lines) {
        Scene scene = newScene(userId, version, place, startsAt, endsAt, SceneType.DIALOGUE);
        ReflectionTestUtils.setField(scene, "lines", lines, String.class);
        return scene;
    }

    // Scene/Question/ScenePartner는 protected 기본 생성자만 있고 생성 팩토리가 없으므로 리플렉션으로 픽스처를 만든다.
    // created_at 등 NOT NULL 컬럼은 반드시 채워야 INSERT가 통과한다.
    private Scene newScene(Long userId, String version, String place,
                           LocalTime startsAt, LocalTime endsAt, SceneType type) {
        Scene scene = BeanUtils.instantiateClass(Scene.class);
        ReflectionTestUtils.setField(scene, "userId", userId);
        ReflectionTestUtils.setField(scene, "date", DATE);
        ReflectionTestUtils.setField(scene, "version", version);
        ReflectionTestUtils.setField(scene, "place", place);
        ReflectionTestUtils.setField(scene, "startsAt", startsAt);
        ReflectionTestUtils.setField(scene, "endsAt", endsAt);
        ReflectionTestUtils.setField(scene, "type", type);
        ReflectionTestUtils.setField(scene, "createdAt", Instant.now());
        return scene;
    }

    private ScenePartner scenePartner(Long sceneId, Long partnerUserId) {
        ScenePartner scenePartner = BeanUtils.instantiateClass(ScenePartner.class);
        ReflectionTestUtils.setField(scenePartner, "sceneId", sceneId);
        ReflectionTestUtils.setField(scenePartner, "userId", partnerUserId);
        ReflectionTestUtils.setField(scenePartner, "createdAt", Instant.now());
        return scenePartner;
    }

    private Question question(Long userId, LocalTime time, QuestionType type, String text, List<String> options) {
        Question question = BeanUtils.instantiateClass(Question.class);
        ReflectionTestUtils.setField(question, "userId", userId);
        ReflectionTestUtils.setField(question, "date", DATE);
        ReflectionTestUtils.setField(question, "version", "v1");
        ReflectionTestUtils.setField(question, "time", time);
        ReflectionTestUtils.setField(question, "type", type);
        ReflectionTestUtils.setField(question, "text", text);
        ReflectionTestUtils.setField(question, "options", options);
        ReflectionTestUtils.setField(question, "isSkipped", false);
        ReflectionTestUtils.setField(question, "createdAt", Instant.now());
        return question;
    }
}
