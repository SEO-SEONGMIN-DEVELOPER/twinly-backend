package com.nidus.twinly.people.integration;

import com.nidus.twinly.activity.domain.SceneType;
import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.entity.ScenePartner;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.block.entity.Block;
import com.nidus.twinly.block.repository.BlockRepository;
import com.nidus.twinly.people.entity.Encounter;
import com.nidus.twinly.people.entity.EncounterPreference;
import com.nidus.twinly.people.repository.EncounterPreferenceRepository;
import com.nidus.twinly.people.repository.EncounterRepository;
import com.nidus.twinly.relationship.entity.Relationship;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PeopleIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate DAY_1 = LocalDate.of(2026, 7, 19);
    private static final LocalDate DAY_2 = LocalDate.of(2026, 7, 20);

    @Autowired
    RelationshipRepository relationshipRepository;

    @Autowired
    SceneRepository sceneRepository;

    @Autowired
    ScenePartnerRepository scenePartnerRepository;

    @Autowired
    EncounterRepository encounterRepository;

    @Autowired
    EncounterPreferenceRepository encounterPreferenceRepository;

    @Autowired
    BlockRepository blockRepository;

    @Test
    @DisplayName("사람 목록 조회: 실제 관계 데이터를 파트너 id 오름차순으로 관통 조회해 친밀도·관계 타입을 내려준다")
    void people_success_end_to_end() throws Exception {
        // given: 나와 관계가 있는 파트너 2명을 실제 DB에 저장 (친밀도 40 / 80)
        User me = saveUser();
        User partner1 = saveUser();
        User partner2 = saveUser();
        saveRelationship(me.getId(), partner1.getId(), DAY_2, 40, "{}");
        saveRelationship(me.getId(), partner2.getId(), DAY_2, 80, "{}");

        // when: 나의 실제 액세스 토큰으로 사람 목록 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/people")
                .header("Authorization", bearer(me.getId())));

        // then: 200 + 파트너 id 오름차순으로 2건, 친밀도에서 파생된 관계 타입까지 내려온다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.people.length()").value(2))
                .andExpect(jsonPath("$.people[0].userId").value(partner1.getId().toString()))
                .andExpect(jsonPath("$.people[0].userName").value(partner1.getGivenName()))
                .andExpect(jsonPath("$.people[0].intimacy").value(40))
                .andExpect(jsonPath("$.people[0].relationshipType").value("friend"))
                .andExpect(jsonPath("$.people[0].isFavorited").value(false))
                .andExpect(jsonPath("$.people[0].isHighlighted").doesNotExist())
                .andExpect(jsonPath("$.people[1].userId").value(partner2.getId().toString()))
                .andExpect(jsonPath("$.people[1].intimacy").value(80))
                .andExpect(jsonPath("$.people[1].relationshipType").value("best_friend"))
                .andExpect(jsonPath("$.page.hasMore").value(false))
                .andExpect(jsonPath("$.page.nextCursor").isEmpty());
    }

    @Test
    @DisplayName("사람 목록 조회: 내가 차단한 상대는 쿼리 단계에서 제외되어 목록에 나오지 않는다")
    void people_excludes_blocked_partner() throws Exception {
        // given: 관계가 있는 파트너 2명 중 한 명을 차단
        User me = saveUser();
        User partner1 = saveUser();
        User partner2 = saveUser();
        saveRelationship(me.getId(), partner1.getId(), DAY_2, 40, "{}");
        saveRelationship(me.getId(), partner2.getId(), DAY_2, 80, "{}");
        blockRepository.save(Block.create(me.getId(), partner2.getId()));

        // when: 사람 목록 조회
        var result = mockMvc.perform(get("/api/v1/people")
                .header("Authorization", bearer(me.getId())));

        // then: 차단한 상대만 빠지고, 페이지 정보도 남은 건수 기준으로 계산된다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.people.length()").value(1))
                .andExpect(jsonPath("$.people[0].userId").value(partner1.getId().toString()))
                .andExpect(jsonPath("$.page.hasMore").value(false));
    }

    @Test
    @DisplayName("프로필 조회: 실제 유저·관계 데이터를 관통해 성+이름과 친밀도 기반 관계 타입을 내려준다")
    void profile_success_end_to_end() throws Exception {
        // given: 친밀도 75인 상대를 실제 DB에 저장
        User me = saveUser();
        User partner = saveUser();
        saveRelationship(me.getId(), partner.getId(), DAY_2, 75, "{}");

        // when: 나의 실제 액세스 토큰으로 프로필 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/people/{userId}/profile", partner.getId().toString())
                .header("Authorization", bearer(me.getId())));

        // then: 200 + 성+이름 결합, 공개 미동의 필드는 null, 차단/즐겨찾기는 false
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(partner.getId().toString()))
                .andExpect(jsonPath("$.userName").value(partner.getFamilyName() + partner.getGivenName()))
                .andExpect(jsonPath("$.intimacy").value(75))
                .andExpect(jsonPath("$.relationshipType").value("best_friend"))
                .andExpect(jsonPath("$.isFavorited").value(false))
                .andExpect(jsonPath("$.isHighlighted").doesNotExist())
                .andExpect(jsonPath("$.isBlocked").value(false))
                .andExpect(jsonPath("$.isDeleted").value(false))
                .andExpect(jsonPath("$.disclosedFields.affiliation").isEmpty())
                .andExpect(jsonPath("$.disclosedFields.affiliationNumber").isEmpty());
    }

    @Test
    @DisplayName("즐겨찾기 등록: 인증·컨트롤러·서비스를 관통해 encounter_preferences 행이 is_favorited=true로 생성된다")
    void favorite_success_end_to_end() throws Exception {
        // given: 두 유저와 그들의 encounter를 실제 DB에 저장 (즐겨찾기 이력은 없음)
        User me = saveUser();
        User partner = saveUser();
        Encounter encounter = encounterRepository.saveAndFlush(Encounter.create(me.getId(), partner.getId()));

        // when: 나의 실제 액세스 토큰으로 즐겨찾기 등록 API 호출
        mockMvc.perform(put("/api/v1/people/{userId}/favorite", partner.getId().toString())
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk());

        // then: DB에 즐겨찾기 행이 실제로 생성되고 값이 true
        EncounterPreference saved = encounterPreferenceRepository
                .findByEncounterIdAndUserId(encounter.getId(), me.getId())
                .orElseThrow();
        assertThat(saved.getIsFavorited()).isTrue();
    }

    @Test
    @DisplayName("즐겨찾기 해제: 기존 encounter_preferences 행의 is_favorited가 false로 실제 갱신된다")
    void deleteFavorites_success_end_to_end() throws Exception {
        // given: 이미 즐겨찾기된 상대를 실제 DB에 저장
        User me = saveUser();
        User partner = saveUser();
        Encounter encounter = encounterRepository.saveAndFlush(Encounter.create(me.getId(), partner.getId()));
        EncounterPreference preference = EncounterPreference.create(encounter.getId(), me.getId());
        preference.changeIsFavorited(true);
        encounterPreferenceRepository.saveAndFlush(preference);

        // when: 나의 실제 액세스 토큰으로 즐겨찾기 해제 API 호출
        mockMvc.perform(delete("/api/v1/people/{userId}/favorite", partner.getId().toString())
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk());

        // then: DB의 즐겨찾기 값이 false로 바뀐다
        EncounterPreference updated = encounterPreferenceRepository
                .findByEncounterIdAndUserId(encounter.getId(), me.getId())
                .orElseThrow();
        assertThat(updated.getIsFavorited()).isFalse();
    }

    @Test
    @DisplayName("친밀도 시계열 조회: from/to 구간의 실제 관계 기록이 날짜 오름차순 포인트로 내려온다")
    void intimacySeries_success_end_to_end() throws Exception {
        // given: 7/19(10), 7/20(60) 두 건의 관계 기록을 실제 DB에 저장
        User me = saveUser();
        User partner = saveUser();
        saveRelationship(me.getId(), partner.getId(), DAY_1, 10, "{}");
        saveRelationship(me.getId(), partner.getId(), DAY_2, 60, "{}");

        // when: 기간·해상도·최대 포인트 수와 함께 친밀도 시계열 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/people/{userId}/intimacy-series", partner.getId().toString())
                .param("from", "2026-07-01")
                .param("to", "2026-07-31")
                .param("resolution", "DAY")
                .param("maxPoints", "10")
                .header("Authorization", bearer(me.getId())));

        // then: 200 + 최신 친밀도와 날짜 오름차순 시계열 2건
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.currentIntimacy").value(60))
                .andExpect(jsonPath("$.intimacySeries.length()").value(2))
                .andExpect(jsonPath("$.intimacySeries[0].date").value("2026-07-19"))
                .andExpect(jsonPath("$.intimacySeries[0].intimacy").value(10))
                .andExpect(jsonPath("$.intimacySeries[1].date").value("2026-07-20"))
                .andExpect(jsonPath("$.intimacySeries[1].intimacy").value(60));
    }

    @Test
    @DisplayName("이벤트 목록 조회: scenes/scene_partners 조인 쿼리를 관통해 장소·미리보기와 친밀도 변화량을 내려준다")
    void events_success_end_to_end() throws Exception {
        // given: 7/20에 상대와 함께한 행동 씬 1건과 7/19->7/20 친밀도 상승 기록을 실제 DB에 저장
        User me = saveUser();
        User partner = saveUser();
        saveRelationship(me.getId(), partner.getId(), DAY_1, 10, "{}");
        saveRelationship(me.getId(), partner.getId(), DAY_2, 40, "{}");
        Scene scene = saveScene(me.getId(), DAY_2, "v1", "카페", "커피를 마셨다", "즐거웠다");
        saveScenePartner(scene.getId(), partner.getId());

        // when: 나의 실제 액세스 토큰으로 이벤트 목록 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/people/{userId}/events", partner.getId().toString())
                .header("Authorization", bearer(me.getId())));

        // then: 200 + 상대 정보와 7/20 이벤트(장소·미리보기·변화량 30·관계 변화)가 내려온다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.partner.userId").value(partner.getId().toString()))
                .andExpect(jsonPath("$.partner.userName").value(partner.getFamilyName() + partner.getGivenName()))
                .andExpect(jsonPath("$.partner.intimacy").value(40))
                .andExpect(jsonPath("$.events.length()").value(1))
                .andExpect(jsonPath("$.events[0].date").value("2026-07-20"))
                .andExpect(jsonPath("$.events[0].place").value("카페"))
                .andExpect(jsonPath("$.events[0].preview").value("커피를 마셨다"))
                .andExpect(jsonPath("$.events[0].intimacyDelta").value(30))
                .andExpect(jsonPath("$.events[0].relationshipChange").value("RELATIONSHIP_SPECIFIC_TYPE_2"))
                .andExpect(jsonPath("$.page.hasMore").value(false));
    }

    @Test
    @DisplayName("이벤트 상세 조회: 해당 날짜에 상대가 참여한 씬만 골라 action 타입 JSON으로 내려준다")
    void event_success_end_to_end() throws Exception {
        // given: 7/20에 상대와 함께한 씬 1건과 상대가 없는 씬 1건을 실제 DB에 저장
        User me = saveUser();
        User partner = saveUser();
        User other = saveUser();
        Scene withPartner = saveScene(me.getId(), DAY_2, "v1", "학교 복도", "복도를 함께 걸었다", "설렜다");
        saveScenePartner(withPartner.getId(), partner.getId());
        Scene withOther = saveScene(me.getId(), DAY_2, "v2", "도서관", "혼자 공부했다", null);
        saveScenePartner(withOther.getId(), other.getId());

        // when: 나의 실제 액세스 토큰으로 이벤트 상세 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/people/{userId}/events/{date}", partner.getId().toString(), "2026-07-20")
                .header("Authorization", bearer(me.getId())));

        // then: 200 + 상대가 참여한 씬 1건만, type=action과 with 목록까지 내려온다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-07-20"))
                .andExpect(jsonPath("$.userId").value(partner.getId().toString()))
                .andExpect(jsonPath("$.version").value("v1"))
                .andExpect(jsonPath("$.scenes.length()").value(1))
                .andExpect(jsonPath("$.scenes[0].sceneId").value(withPartner.getId().toString()))
                .andExpect(jsonPath("$.scenes[0].type").value("action"))
                .andExpect(jsonPath("$.scenes[0].place").value("학교 복도"))
                .andExpect(jsonPath("$.scenes[0].narration").value("복도를 함께 걸었다"))
                .andExpect(jsonPath("$.scenes[0].mind").value("설렜다"))
                .andExpect(jsonPath("$.scenes[0].with.length()").value(1))
                .andExpect(jsonPath("$.scenes[0].with[0].userId").value(partner.getId().toString()))
                .andExpect(jsonPath("$.scenes[0].with[0].userName").value(partner.getFamilyName() + partner.getGivenName()));
    }

    @Test
    @DisplayName("알게 된 사실 조회: 최신 관계 기록의 partner_model 값이 그대로 내려온다")
    void learnedFacts_success_end_to_end() throws Exception {
        // given: 날짜가 다른 관계 기록 2건을 저장 (최신 기록의 partnerModel이 응답되어야 함)
        User me = saveUser();
        User partner = saveUser();
        saveRelationship(me.getId(), partner.getId(), DAY_1, 10, "예전에 알게 된 사실");
        saveRelationship(me.getId(), partner.getId(), DAY_2, 50, "커피를 좋아한다");

        // when: 나의 실제 액세스 토큰으로 알게 된 사실 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/people/{userId}/learned-facts", partner.getId().toString())
                .header("Authorization", bearer(me.getId())));

        // then: 200 + 최신 관계 기록의 partnerModel이 내려온다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.learnedFacts").value("커피를 좋아한다"));
    }

    // ------------------------------------------------------------------ fixtures

    private Relationship saveRelationship(Long userId, Long partnerUserId, LocalDate date, int intimacy, String partnerModel) {
        Relationship relationship = newInstance(Relationship.class);
        ReflectionTestUtils.setField(relationship, "userId", userId);
        ReflectionTestUtils.setField(relationship, "partnerUserId", partnerUserId);
        ReflectionTestUtils.setField(relationship, "date", date);
        ReflectionTestUtils.setField(relationship, "intimacy", intimacy);
        ReflectionTestUtils.setField(relationship, "partnerModel", partnerModel);
        ReflectionTestUtils.setField(relationship, "createdAt", Instant.now());
        return relationshipRepository.saveAndFlush(relationship);
    }

    private Scene saveScene(Long userId, LocalDate date, String version, String place, String narration, String mind) {
        Scene scene = newInstance(Scene.class);
        ReflectionTestUtils.setField(scene, "userId", userId);
        ReflectionTestUtils.setField(scene, "date", date);
        ReflectionTestUtils.setField(scene, "version", version);
        ReflectionTestUtils.setField(scene, "place", place);
        ReflectionTestUtils.setField(scene, "startsAt", LocalDateTime.of(date, LocalTime.of(9, 0)));
        ReflectionTestUtils.setField(scene, "endsAt", LocalDateTime.of(date, LocalTime.of(10, 0)));
        ReflectionTestUtils.setField(scene, "type", SceneType.ACTION);
        ReflectionTestUtils.setField(scene, "narration", narration);
        ReflectionTestUtils.setField(scene, "mind", mind);
        ReflectionTestUtils.setField(scene, "createdAt", Instant.now());
        return sceneRepository.saveAndFlush(scene);
    }

    private ScenePartner saveScenePartner(Long sceneId, Long userId) {
        ScenePartner scenePartner = newInstance(ScenePartner.class);
        ReflectionTestUtils.setField(scenePartner, "sceneId", sceneId);
        ReflectionTestUtils.setField(scenePartner, "userId", userId);
        ReflectionTestUtils.setField(scenePartner, "createdAt", Instant.now());
        return scenePartnerRepository.saveAndFlush(scenePartner);
    }

    /** 엔티티가 protected 기본 생성자만 가지므로 리플렉션으로 인스턴스를 만든다. */
    private <T> T newInstance(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("테스트 엔티티 생성 실패: " + type.getName(), e);
        }
    }

    @Test
    @DisplayName("이벤트 상세 조회: 존재하지 않는 상대면 404 USER_NOT_FOUND를 반환한다 (목록 조회와 같은 규약)")
    void event_unknown_partner_returns_404() throws Exception {
        // given: 실제 유저 1명
        User me = saveUser();

        // when & then: 상대가 마스터 데이터에 없으므로 빈 200이 아니라 404
        mockMvc.perform(get("/api/v1/people/{userId}/events/{date}", "99999999", "2026-07-20")
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }
}
