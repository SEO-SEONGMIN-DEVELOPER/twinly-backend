package com.nidus.twinly.showcase.integration;

import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.entity.ScenePartner;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.block.entity.Block;
import com.nidus.twinly.block.repository.BlockRepository;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import com.nidus.twinly.season.repository.SeasonRepository;
import com.nidus.twinly.showcase.repository.ShowcaseRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShowcaseIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    ShowcaseRepository showcaseRepository;

    @Autowired
    SceneRepository sceneRepository;

    @Autowired
    ScenePartnerRepository scenePartnerRepository;

    @Autowired
    SeasonRepository seasonRepository;

    @Autowired
    SeasonParticipationRepository seasonParticipationRepository;

    @Autowired
    BlockRepository blockRepository;

    Season season;

    @BeforeEach
    void setUpSeason() {
        // given: 활성 시즌이 하나 있어야 후보 조회가 성립한다
        season = seasonRepository.save(Season.create(Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600)));
    }

    @Test
    @DisplayName("관람 조회: 후보가 실제 쿼리로 뽑혀 배정 행이 생기고, 이름은 성만 남긴 채 내려간다")
    void today_end_to_end() throws Exception {
        // given: 오늘 장면이 있는 시즌 참가자와 동행자, 그리고 관람자
        User viewer = saveUser();
        User target = saveParticipant();
        User partner = saveUser();
        Scene scene = saveActionScene(target, "{user_" + target.getId() + "}이 뛰어서 등교했다.");
        scenePartnerRepository.save(ScenePartner.create(scene.getId(), partner.getId()));

        // when: 관람 API 호출
        mockMvc.perform(get("/api/v1/showcases/today")
                        .header("Authorization", bearer(viewer.getId())))
                // then: 대상은 userRef 1, 동행자는 2 + 이름은 성+OO + 실제 유저 id는 나가지 않는다
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userRef").value("1"))
                .andExpect(jsonPath("$.date").value(KstTimes.today().toString()))
                .andExpect(jsonPath("$.scenes[0].narration").value(target.getFamilyName() + "OO이 뛰어서 등교했다."))
                .andExpect(jsonPath("$.scenes[0].with[0]").value("2"))
                .andExpect(jsonPath("$.userInfos[0].userRef").value("1"))
                .andExpect(jsonPath("$.userInfos[0].userName").value(target.getFamilyName() + "OO"))
                .andExpect(jsonPath("$.userInfos[0].organization").isNotEmpty())
                .andExpect(jsonPath("$.userInfos[0].profilePhoto").doesNotExist())
                .andExpect(jsonPath("$.userCounts.total").isNumber())
                .andExpect(jsonPath("$.scenes[0].sceneId").value(scene.getId().toString()));

        // then: 오늘자 배정 행이 실제로 생성됐다
        assertThat(showcaseRepository.findByViewerUserIdAndDate(viewer.getId(), KstTimes.today()))
                .get()
                .satisfies(showcase -> assertThat(showcase.getTargetUserId()).isEqualTo(target.getId()));
    }

    @Test
    @DisplayName("관람 조회 재요청: 같은 날 다시 호출해도 배정이 늘지 않고 같은 대상이 나온다")
    void today_is_fixed_per_day() throws Exception {
        // given: 후보가 두 명이라 무작위 선택이 흔들릴 수 있는 상황
        User viewer = saveUser();
        saveActionScene(saveParticipant(), "등교했다.");
        saveActionScene(saveParticipant(), "등교했다.");

        // when: 같은 날 두 번 호출
        String first = todayShowcaseId(viewer);
        String second = todayShowcaseId(viewer);

        // then: 같은 배정 id + 행은 하나뿐
        assertThat(first).isEqualTo(second);
        assertThat(showcaseRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("배정 규칙: 본인·차단 상대·시즌 미참가자·장면 없는 유저는 후보에서 빠져 404가 난다")
    void today_without_candidate_returns_404() throws Exception {
        // given: 관람자 본인은 오늘 장면이 있는 참가자이고, 나머지 후보는 전부 조건에서 탈락한다
        User viewer = saveParticipant();
        saveActionScene(viewer, "내 하루다.");

        User blocked = saveParticipant();
        saveActionScene(blocked, "차단한 상대의 하루다.");
        blockRepository.save(Block.create(viewer.getId(), blocked.getId()));

        User notParticipant = saveUser();
        saveActionScene(notParticipant, "시즌에 참가하지 않았다.");

        saveParticipant();

        // when & then: 남는 후보가 없어 404와 도메인 코드가 나간다
        mockMvc.perform(get("/api/v1/showcases/today")
                        .header("Authorization", bearer(viewer.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHOWCASE_TARGET_NOT_FOUND"));

        // then: 배정 행도 생기지 않는다
        assertThat(showcaseRepository.count()).isZero();
    }

    @Test
    @DisplayName("인증 헤더가 없으면 401이고 배정 행도 생기지 않는다")
    void without_auth_returns_401() throws Exception {
        // when & then: 인증 없이 호출하면 401
        mockMvc.perform(get("/api/v1/showcases/today"))
                .andExpect(status().isUnauthorized());

        // then: DB에 아무것도 저장되지 않는다
        assertThat(showcaseRepository.count()).isZero();
    }

    private String todayShowcaseId(User viewer) throws Exception {
        String body = mockMvc.perform(get("/api/v1/showcases/today")
                        .header("Authorization", bearer(viewer.getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return com.jayway.jsonpath.JsonPath.read(body, "$.showcaseId");
    }

    private User saveParticipant() {
        User user = saveUser();
        seasonParticipationRepository.upsert(user.getId(), season.getId());

        return user;
    }

    private Scene saveActionScene(User user, String narration) {
        LocalDate today = KstTimes.today();

        return sceneRepository.save(Scene.createAction(
                user.getId(), today, "v1", "학교 정문",
                LocalDateTime.of(today, java.time.LocalTime.of(9, 0)),
                LocalDateTime.of(today, java.time.LocalTime.of(9, 40)),
                narration, "아슬아슬했다."));
    }
}
