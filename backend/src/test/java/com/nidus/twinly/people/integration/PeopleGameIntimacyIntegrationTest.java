package com.nidus.twinly.people.integration;

import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.entity.ScenePartner;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.relationship.entity.IntimacyBonus;
import com.nidus.twinly.relationship.entity.Relationship;
import com.nidus.twinly.relationship.repository.IntimacyBonusRepository;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PeopleGameIntimacyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    RelationshipRepository relationshipRepository;

    @Autowired
    IntimacyBonusRepository intimacyBonusRepository;

    @Autowired
    SceneRepository sceneRepository;

    @Autowired
    ScenePartnerRepository scenePartnerRepository;

    private LocalDate twoDaysAgo;
    private LocalDate yesterday;
    private User me;
    private User partner;

    @BeforeEach
    void setUp() {
        // given: 그저께 0시 기준 30, 어제 0시 기준 40(그저께 게임 +3이 반영된 값)인 시뮬레이션 기록과
        //        그저께 12시 +3, 어제 12시 +5 게임 점수, 두 날 모두 함께한 장면
        LocalDate today = KstTimes.today();
        twoDaysAgo = today.minusDays(2);
        yesterday = today.minusDays(1);
        me = saveUser();
        partner = saveUser();
        saveRelationship(twoDaysAgo, 30);
        saveRelationship(yesterday, 40);
        saveBonus(twoDaysAgo.atTime(12, 0), 3);
        saveBonus(yesterday.atTime(12, 0), 5);
        saveSceneWithPartner(twoDaysAgo);
        saveSceneWithPartner(yesterday);
    }

    @Test
    @DisplayName("사람 목록과 프로필 v1·v2는 최종 친밀도(어제 40 + 그 뒤 게임 5)와 지금까지의 게임 몫(3 + 5)을 내려준다")
    void people_and_profiles_return_final_and_game_intimacy() throws Exception {
        // when & then: 사람 목록
        mockMvc.perform(get("/api/v1/people").header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.people[0].intimacy").value(45))
                .andExpect(jsonPath("$.people[0].gameIntimacy").value(8));

        // when & then: 프로필 v1
        mockMvc.perform(get("/api/v1/people/{userId}/profile", partner.getId().toString())
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intimacy").value(45))
                .andExpect(jsonPath("$.gameIntimacy").value(8));

        // when & then: 프로필 v2
        mockMvc.perform(get("/api/v2/people/{userId}/profile", partner.getId().toString())
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intimacy").value(45))
                .andExpect(jsonPath("$.gameIntimacy").value(8));
    }

    @Test
    @DisplayName("친밀도 그래프는 날짜마다 그날이 끝날 때까지의 게임 점수를 반영한다")
    void intimacySeries_reflects_game_points_by_date() throws Exception {
        // when: 친밀도 그래프 조회
        var result = mockMvc.perform(get("/api/v1/people/{userId}/intimacy-series", partner.getId().toString())
                .header("Authorization", bearer(me.getId())));

        // then: 그저께 30 + 3, 어제 40 + 5, 오늘은 어제와 같다. 게임 몫은 그날까지의 누적
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.currentIntimacy").value(45))
                .andExpect(jsonPath("$.currentGameIntimacy").value(8))
                .andExpect(jsonPath("$.intimacySeries.length()").value(3))
                .andExpect(jsonPath("$.intimacySeries[0].date").value(twoDaysAgo.toString()))
                .andExpect(jsonPath("$.intimacySeries[0].intimacy").value(33))
                .andExpect(jsonPath("$.intimacySeries[0].gameIntimacy").value(3))
                .andExpect(jsonPath("$.intimacySeries[1].date").value(yesterday.toString()))
                .andExpect(jsonPath("$.intimacySeries[1].intimacy").value(45))
                .andExpect(jsonPath("$.intimacySeries[1].gameIntimacy").value(8))
                .andExpect(jsonPath("$.intimacySeries[2].intimacy").value(45))
                .andExpect(jsonPath("$.intimacySeries[2].gameIntimacy").value(8));
    }

    @Test
    @DisplayName("이벤트 목록의 상대 친밀도는 최종값이고, 전날 대비 변화량에도 게임 점수가 반영된다")
    void events_reflect_game_points_in_partner_and_delta() throws Exception {
        // when: 이벤트 목록 조회
        var result = mockMvc.perform(get("/api/v1/people/{userId}/events", partner.getId().toString())
                .header("Authorization", bearer(me.getId())));

        // then: 어제 변화량은 시뮬레이션만의 10(40 - 30)이 아니라 45 - 33 = 12
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.partner.intimacy").value(45))
                .andExpect(jsonPath("$.partner.gameIntimacy").value(8))
                .andExpect(jsonPath("$.events[0].date").value(yesterday.toString()))
                .andExpect(jsonPath("$.events[0].intimacyDelta").value(12));
    }

    private void saveRelationship(LocalDate date, int intimacy) {
        relationshipRepository.save(Relationship.create(me.getId(), date, "v1", partner.getId(), intimacy, "model",
                date.atTime(23, 0), KstTimes.toInstant(date.atStartOfDay())));
    }

    private void saveBonus(LocalDateTime kstTime, int amount) {
        IntimacyBonus bonus = IntimacyBonus.create(me.getId(), partner.getId(), amount);
        ReflectionTestUtils.setField(bonus, "createdAt", KstTimes.toInstant(kstTime));
        intimacyBonusRepository.save(bonus);
    }

    private void saveSceneWithPartner(LocalDate date) {
        Scene scene = sceneRepository.save(Scene.createAction(me.getId(), date, "v1", "카페", null,
                date.atTime(9, 0), date.atTime(10, 0), "커피를 마셨다", null));
        scenePartnerRepository.save(ScenePartner.create(scene.getId(), partner.getId()));
    }
}
