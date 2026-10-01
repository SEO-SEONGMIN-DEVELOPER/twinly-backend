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
import org.springframework.test.util.ReflectionTestUtils;

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
    @DisplayName("관람 조회: 후보가 실제 쿼리로 뽑혀 배정 행이 생기고, 이름은 닉네임으로 내려간다")
    void today_end_to_end() throws Exception {
        // given: 오늘 장면이 있는 시즌 참가자와 동행자, 그리고 관람자
        User viewer = saveUser();
        User target = saveSeedParticipant();
        User partner = saveUser();
        Scene scene = saveActionScene(target, "{user_" + target.getId() + "}이 뛰어서 등교했다.");
        scenePartnerRepository.save(ScenePartner.create(scene.getId(), partner.getId()));

        // when: 관람 API 호출
        mockMvc.perform(get("/api/v1/showcases/today")
                        .header("Authorization", bearer(viewer.getId())))
                // then: 대상은 userRef 1, 동행자는 2 + 이름은 닉네임 + 실제 유저 id는 나가지 않는다
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userRef").value("1"))
                .andExpect(jsonPath("$.date").value(KstTimes.today().toString()))
                .andExpect(jsonPath("$.scenes[0].narration").value(target.getNickname() + "이 뛰어서 등교했다."))
                .andExpect(jsonPath("$.scenes[0].with[0]").value("2"))
                .andExpect(jsonPath("$.userInfos[0].userRef").value("1"))
                .andExpect(jsonPath("$.userInfos[0].userName").value(target.getNickname()))
                .andExpect(jsonPath("$.userInfos[0].organization").isNotEmpty())
                .andExpect(jsonPath("$.userInfos[0].profilePhoto").doesNotExist())
                .andExpect(jsonPath("$.userCounts.total").isNumber())
                .andExpect(jsonPath("$.userCounts.organization").isNotEmpty())
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
        saveActionScene(saveSeedParticipant(), "등교했다.");
        saveActionScene(saveSeedParticipant(), "등교했다.");

        // when: 같은 날 두 번 호출
        String first = todayShowcaseId(viewer);
        String second = todayShowcaseId(viewer);

        // then: 같은 배정 id + 행은 하나뿐
        assertThat(first).isEqualTo(second);
        assertThat(showcaseRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("배정 규칙: 같은 학교 후보가 있으면 다른 학교 후보가 여럿이어도 같은 학교 유저가 배정된다")
    void today_prefers_same_organization() throws Exception {
        // given: 관람자와 같은 학교 후보 한 명, 다른 학교 후보 세 명
        User viewer = saveUserInOrganization("sameOrganizationHash");
        User sameSchool = markShowcaseSeed(saveUserInOrganization("sameOrganizationHash"));
        seasonParticipationRepository.upsert(sameSchool.getId(), season.getId());
        saveActionScene(sameSchool, "같은 학교다.");
        for (int i = 0; i < 3; i++) {
            saveActionScene(saveSeedParticipant(), "다른 학교다.");
        }

        // when: 오늘 관람 조회
        todayShowcaseId(viewer);

        // then: 같은 학교 유저가 대상으로 저장된다
        assertThat(showcaseRepository.findAll()).singleElement()
                .satisfies(showcase -> assertThat(showcase.getTargetUserId()).isEqualTo(sameSchool.getId()));
    }

    @Test
    @DisplayName("배정 규칙: 같은 학교 실제 가입자가 있어도 쇼케이스 시드 유저만 배정되고, 같은 학교 시드 유저가 없으면 다른 학교 시드 유저로 넘어간다")
    void today_picks_only_showcase_seed_users() throws Exception {
        // given: 관람자와 같은 학교인 실제 가입자(시드 아님)와 다른 학교 시드 유저 한 명
        User viewer = saveUserInOrganization("sameOrganizationHash");
        User sameSchoolRealUser = saveUserInOrganization("sameOrganizationHash");
        seasonParticipationRepository.upsert(sameSchoolRealUser.getId(), season.getId());
        saveActionScene(sameSchoolRealUser, "같은 학교 실제 가입자다.");
        User otherSchoolSeed = saveSeedParticipant();
        saveActionScene(otherSchoolSeed, "다른 학교 시드 유저다.");

        // when: 오늘 관람 조회
        todayShowcaseId(viewer);

        // then: 실제 가입자는 건너뛰고 다른 학교 시드 유저가 배정된다
        assertThat(showcaseRepository.findAll()).singleElement()
                .satisfies(showcase -> assertThat(showcase.getTargetUserId()).isEqualTo(otherSchoolSeed.getId()));
    }

    @Test
    @DisplayName("배정 규칙: 시드 후보가 없으면 실제 유저 중에서 뽑고, 다른 학교 후보가 여럿이어도 같은 학교 유저가 배정된다")
    void today_falls_back_to_real_users_preferring_same_organization() throws Exception {
        // given: 시드 후보는 없고, 관람자와 같은 학교 실제 유저 한 명과 다른 학교 실제 유저 세 명
        User viewer = saveUserInOrganization("sameOrganizationHash");
        User sameSchoolRealUser = saveUserInOrganization("sameOrganizationHash");
        seasonParticipationRepository.upsert(sameSchoolRealUser.getId(), season.getId());
        saveActionScene(sameSchoolRealUser, "같은 학교 실제 가입자다.");
        for (int i = 0; i < 3; i++) {
            User otherSchoolRealUser = saveUser();
            seasonParticipationRepository.upsert(otherSchoolRealUser.getId(), season.getId());
            saveActionScene(otherSchoolRealUser, "다른 학교 실제 가입자다.");
        }

        // when: 오늘 관람 조회
        todayShowcaseId(viewer);

        // then: 같은 학교 실제 유저가 배정된다
        assertThat(showcaseRepository.findAll()).singleElement()
                .satisfies(showcase -> assertThat(showcase.getTargetUserId()).isEqualTo(sameSchoolRealUser.getId()));
    }

    @Test
    @DisplayName("배정 규칙: 본인·차단 상대·시즌 미참가자·장면 없는 유저는 후보에서 빠져 404가 난다")
    void today_without_candidate_returns_404() throws Exception {
        // given: 관람자 본인은 오늘 장면이 있는 참가자이고, 나머지 후보는 전부 조건에서 탈락한다
        User viewer = saveSeedParticipant();
        saveActionScene(viewer, "내 하루다.");

        User blocked = saveSeedParticipant();
        saveActionScene(blocked, "차단한 상대의 하루다.");
        blockRepository.save(Block.create(viewer.getId(), blocked.getId()));

        User notParticipant = saveUser();
        saveActionScene(notParticipant, "시즌에 참가하지 않았다.");

        saveSeedParticipant();

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

    private User saveUserInOrganization(String organizationHash) {
        User user = saveUser();
        ReflectionTestUtils.setField(user, "organizationHash", organizationHash);

        return userRepository.save(user);
    }

    private User saveSeedParticipant() {
        User user = markShowcaseSeed(saveUser());
        seasonParticipationRepository.upsert(user.getId(), season.getId());

        return user;
    }

    private User markShowcaseSeed(User user) {
        ReflectionTestUtils.setField(user, "isShowcaseSeed", true);

        return userRepository.save(user);
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
