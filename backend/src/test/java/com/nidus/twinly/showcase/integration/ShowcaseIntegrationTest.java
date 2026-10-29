package com.nidus.twinly.showcase.integration;

import com.jayway.jsonpath.JsonPath;
import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.entity.ScenePartner;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.block.entity.Block;
import com.nidus.twinly.block.repository.BlockRepository;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import com.nidus.twinly.season.repository.SeasonRepository;
import com.nidus.twinly.showcase.repository.ShowcaseRepository;
import com.nidus.twinly.showcase.repository.ShowcaseUserRefRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.world.entity.WorldTrack;
import com.nidus.twinly.world.repository.WorldTrackRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.ResultActions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShowcaseIntegrationTest extends AbstractIntegrationTest {

    private static final int POOL_NUMBER = 7;
    private static final String SECTOR_CODE = "S034";
    private static final long OUTSIDER_REF_MIN = 1L << 40;

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

    @Autowired
    WorldTrackRepository worldTrackRepository;

    @Autowired
    ShowcaseUserRefRepository showcaseUserRefRepository;

    @PersistenceContext
    EntityManager entityManager;

    Season season;

    LocalDate today;
    User viewer;
    User target;
    User partner;
    User outsider;

    @BeforeEach
    void setUpSeason() {
        // given: 활성 시즌이 하나 있어야 후보 조회가 성립한다
        season = seasonRepository.save(Season.create(Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600)));
    }

    @Test
    @DisplayName("관람 조회: 후보가 실제 쿼리로 뽑혀 배정 행이 생기고, 이름은 가명(랜덤 성+OO)으로 내려간다")
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
                // then: 대상은 userRef 1, 동행자는 2 + 이름은 가명 + 실제 유저 id는 나가지 않는다
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userRef").value("1"))
                .andExpect(jsonPath("$.date").value(KstTimes.today().toString()))
                .andExpect(jsonPath("$.scenes[0].narration").value(matchesPattern("[가-힣]OO이 뛰어서 등교했다\\.")))
                .andExpect(jsonPath("$.scenes[0].placeCode").value("SCHOOL_GATE"))
                .andExpect(jsonPath("$.scenes[0].with[0]").value("2"))
                .andExpect(jsonPath("$.userInfos[0].userRef").value("1"))
                .andExpect(jsonPath("$.userInfos[0].userName").value(matchesPattern("[가-힣]OO")))
                .andExpect(jsonPath("$.userInfos[0].organization").isNotEmpty())
                .andExpect(jsonPath("$.userInfos[0].profilePhoto").doesNotExist())
                .andExpect(jsonPath("$.userCounts.total").isNumber())
                .andExpect(jsonPath("$.userCounts.organization").isNotEmpty())
                .andExpect(jsonPath("$.scenes[0].sceneId").value(scene.getId().toString()));

        // then: 오늘자 배정 행이 실제로 생성됐다
        assertThat(showcaseRepository.findWithLockByViewerUserIdAndDate(viewer.getId(), KstTimes.today()))
                .get()
                .satisfies(showcase -> assertThat(showcase.getTargetUserId()).isEqualTo(target.getId()));
    }

    @Test
    @DisplayName("관람 조회 v2: 대상의 이동 장면이 move 로 출발지·도착지·이동 정보와 userRef 동행자를 담아 내려가고, v1 은 action 으로 내려간다")
    void today_v2_move_scene_end_to_end() throws Exception {
        // given: 오늘 동행자와 함께 집에서 카페로 이동한 시드 참가자와 관람자
        User viewer = saveUser();
        User target = saveSeedParticipant();
        User partner = saveUser();
        LocalDate today = KstTimes.today();
        Scene move = sceneRepository.save(Scene.createMove(
                target.getId(), today, "v1", "집", "P0258", "학생 거리 탐앤탐스", "P0158",
                LocalDateTime.of(today, java.time.LocalTime.of(11, 0)),
                LocalDateTime.of(today, java.time.LocalTime.of(11, 20)),
                "transit", "sha256:89af", "{user_" + partner.getId() + "}과 카페로 향했다", null));
        scenePartnerRepository.save(ScenePartner.create(move.getId(), partner.getId()));

        // when: 새 앱이 쓰는 v2 관람 API 호출
        mockMvc.perform(get("/api/v2/showcases/today")
                        .header("Authorization", bearer(viewer.getId())))
                // then: move 모양 + 동행자는 userRef 2 + 문장 속 이름은 가명
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userRef").value("1"))
                .andExpect(jsonPath("$.scenes[0].sceneId").value(move.getId().toString()))
                .andExpect(jsonPath("$.scenes[0].type").value("move"))
                .andExpect(jsonPath("$.scenes[0].fromPlace").value("집"))
                .andExpect(jsonPath("$.scenes[0].fromPlaceCode").value("P0258"))
                .andExpect(jsonPath("$.scenes[0].place").value("학생 거리 탐앤탐스"))
                .andExpect(jsonPath("$.scenes[0].placeCode").value("P0158"))
                .andExpect(jsonPath("$.scenes[0].with[0]").value("2"))
                .andExpect(jsonPath("$.scenes[0].travelMode").value("transit"))
                .andExpect(jsonPath("$.scenes[0].mapVersion").value("sha256:89af"))
                .andExpect(jsonPath("$.scenes[0].narration").value(matchesPattern("[가-힣]OO과 카페로 향했다")));

        // then: v2 호출도 v1 과 같이 오늘자 배정 행을 만든다
        assertThat(showcaseRepository.findWithLockByViewerUserIdAndDate(viewer.getId(), KstTimes.today()))
                .get()
                .satisfies(showcase -> assertThat(showcase.getTargetUserId()).isEqualTo(target.getId()));

        // when: 출시된 앱이 쓰는 v1 관람 API 호출 (같은 날이라 같은 대상이 배정돼 있다)
        mockMvc.perform(get("/api/v1/showcases/today")
                        .header("Authorization", bearer(viewer.getId())))
                // then: 구버전 디코더가 아는 action 으로 내려가고 move 전용 필드는 없다
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenes[0].type").value("action"))
                .andExpect(jsonPath("$.scenes[0].place").value("학생 거리 탐앤탐스"))
                .andExpect(jsonPath("$.scenes[0].with[0]").value("2"))
                .andExpect(jsonPath("$.scenes[0].fromPlace").doesNotExist())
                .andExpect(jsonPath("$.scenes[0].travelMode").doesNotExist());
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
    @DisplayName("배정 규칙: 본인·내가 차단한 상대·나를 차단한 상대·시즌 미참가자·장면 없는 유저는 후보에서 빠져 404가 난다")
    void today_without_candidate_returns_404() throws Exception {
        // given: 관람자 본인은 오늘 장면이 있는 참가자이고, 나머지 후보는 전부 조건에서 탈락한다
        User viewer = saveSeedParticipant();
        saveActionScene(viewer, "내 하루다.");

        User blocked = saveSeedParticipant();
        saveActionScene(blocked, "차단한 상대의 하루다.");
        blockRepository.save(Block.create(viewer.getId(), blocked.getId()));

        User blockedMe = saveSeedParticipant();
        saveActionScene(blockedMe, "나를 차단한 상대의 하루다.");
        blockRepository.save(Block.create(blockedMe.getId(), viewer.getId()));

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

    @Test
    @DisplayName("관람 조회 v2: 인증 헤더가 없으면 401이고 배정 행도 생기지 않는다")
    void today_v2_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 v2 관람 API 호출
        mockMvc.perform(get("/api/v2/showcases/today"))
                // then: 401 반환
                .andExpect(status().isUnauthorized());

        // then: DB에 아무것도 저장되지 않는다
        assertThat(showcaseRepository.count()).isZero();
    }

    @Test
    @DisplayName("둘러보기 대상의 풀을 보여 주고, 대상은 user:1·동행자는 둘러보기와 같은 번호와 가상 이름, 나머지는 큰 번호와 누군가의 트윈이다")
    void showcase_sector_uses_showcase_numbering() throws Exception {
        // given: 대상·동행자·둘러보기에 없는 트윈이 함께 있는 공용 트랙과, 대상의 개인 트랙
        setUpWorld();
        saveWorldTrack(WorldTrack.PUBLIC_VIEWER_USER_ID, """
                {"poolNumber": "7", "date": "%1$s", "sectorCode": "S034", "layoutVersion": "sha256:aa", "viewerUserId": null,
                 "actors": [
                   {"ref": "user:%2$d", "segments": [{"start": "%1$sT13:00:00", "end": "%1$sT13:30:00"}]},
                   {"ref": "user:%3$d", "segments": [{"start": "%1$sT13:00:00", "end": "%1$sT13:30:00"}]},
                   {"ref": "user:%4$d", "segments": [{"start": "%1$sT13:00:00", "end": "%1$sT13:30:00"}]},
                   {"ref": "npc:npc082", "segments": [{"start": "%1$sT08:00:00", "end": "%1$sT19:00:00"}]}
                 ],
                 "talks": [{"start": "%1$sT13:10:00", "end": "%1$sT13:11:00", "members": ["user:%2$d", "user:%4$d"]}],
                 "gestures": [{"ref": "user:%2$d", "start": "%1$sT13:05:00", "end": "%1$sT13:05:00"}],
                 "objects": [{"objectId": "P0103-o3", "initial": "닫힘", "changes": [{"at": "%1$sT13:20:00", "state": "열림", "by": "user:%3$d"}]}],
                 "events": [],
                 "items": [{"owner": "user:%4$d", "item": "물", "start": "%1$sT13:00:00", "end": "%1$sT13:30:00"}]}
                """.formatted(today, target.getId(), partner.getId(), outsider.getId()));
        saveWorldTrack(target.getId(), """
                {"poolNumber": "7", "date": "%1$s", "sectorCode": "S034", "layoutVersion": "sha256:aa", "viewerUserId": "%2$d",
                 "actors": [{"ref": "extra:SECRET", "segments": [{"start": "%1$sT13:00:00", "end": "%1$sT13:30:00"}]}],
                 "talks": [], "gestures": [], "objects": [], "events": [], "items": []}
                """.formatted(today, target.getId()));
        flushAndClear();
        String showcase = mockMvc.perform(get("/api/v2/showcases/today").header("Authorization", bearer(viewer.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refVersion").value(0))
                .andReturn().getResponse().getContentAsString();

        // when: 오늘 둘러보기 기준 건물 화면 조회
        String body = todaySector(today).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        // then: 둘러보기와 같은 showcaseId·번호·가상 이름
        assertThat(JsonPath.<String>read(body, "$.showcaseId")).isEqualTo(JsonPath.<String>read(showcase, "$.showcaseId"));
        assertThat(JsonPath.<Integer>read(body, "$.refVersion")).isZero();
        assertThat(JsonPath.<String>read(body, "$.focusRef")).isEqualTo("user:1");
        assertThat(JsonPath.<String>read(body, "$.poolNumber")).isEqualTo("7");
        assertThat(JsonPath.<String>read(body, "$.actors[0].ref")).isEqualTo("user:1");
        assertThat(JsonPath.<String>read(body, "$.actors[0].displayName")).isEqualTo(JsonPath.<String>read(showcase, "$.userInfos[0].userName"));
        assertThat(JsonPath.<String>read(body, "$.actors[1].ref")).isEqualTo("user:2");
        assertThat(JsonPath.<String>read(body, "$.actors[1].displayName")).isEqualTo(JsonPath.<String>read(showcase, "$.userInfos[1].userName"));

        // then: 둘러보기에 없는 트윈은 겹치지 않는 큰 번호이고, 모든 칸에서 같은 번호다
        String outsiderRef = JsonPath.read(body, "$.actors[2].ref");
        assertThat(Long.parseLong(outsiderRef.substring("user:".length()))).isGreaterThanOrEqualTo(OUTSIDER_REF_MIN);
        assertThat(JsonPath.<String>read(body, "$.actors[2].displayName")).isEqualTo("누군가의 트윈");
        assertThat(JsonPath.<String>read(body, "$.actors[2].gender")).isEqualTo("male");
        assertThat(JsonPath.<List<String>>read(body, "$.talks[0].members")).containsExactly("user:1", outsiderRef);
        assertThat(JsonPath.<String>read(body, "$.items[0].owner")).isEqualTo(outsiderRef);
        assertThat(JsonPath.<String>read(body, "$.gestures[0].ref")).isEqualTo("user:1");
        assertThat(JsonPath.<String>read(body, "$.objects[0].changes[0].by")).isEqualTo("user:2");

        // then: user: 로 나가는 값은 둘러보기 번호뿐이고(실제 ID 없음), 대상의 개인 트랙은 섞이지 않는다
        assertThat(Pattern.compile("user:\\d+").matcher(body).results().map(MatchResult::group).distinct())
                .containsExactlyInAnyOrder("user:1", "user:2", outsiderRef);
        assertThat(body).doesNotContain("extra:SECRET");

        // when & then: 다시 불러도 같은 번호
        String again = todaySector(today).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(again, "$.actors[2].ref")).isEqualTo(outsiderRef);

        // then: DB 번호표에는 둘러보기에 나온 대상·동행자만 저장되고, 바깥 트윈은 저장되지 않는다
        Long showcaseId = Long.valueOf(JsonPath.<String>read(body, "$.showcaseId"));
        assertThat(showcaseUserRefRepository.findAllByShowcaseId(showcaseId))
                .extracting(ref -> ref.getUserId() + ":" + ref.getUserRef())
                .containsExactlyInAnyOrder(target.getId() + ":1", partner.getId() + ":2");
    }

    @Test
    @DisplayName("AI 가 대상의 하루를 다시 보내 장면 순서가 바뀌어도, 같은 둘러보기 안에서 동행자 번호는 그대로다")
    void showcase_numbering_survives_resimulation() throws Exception {
        // given: 처음 조회로 동행자가 2번을 받는다
        setUpWorld();
        mockMvc.perform(get("/api/v2/showcases/today").header("Authorization", bearer(viewer.getId())))
                .andExpect(jsonPath("$.userInfos[1].userRef").value("2"));

        // when: 하루를 다시 받아 새 동행자의 장면이 먼저 저장되고 기존 동행자의 장면이 뒤에 다시 저장된다
        User newPartner = saveUserInPool();
        scenePartnerRepository.deleteAll();
        sceneRepository.deleteAll();
        saveTargetSceneWith(newPartner, 8);
        saveTargetSceneWith(partner, 9);
        flushAndClear();

        // then: 기존 동행자는 2 그대로, 새 동행자는 3
        String showcase = mockMvc.perform(get("/api/v2/showcases/today").header("Authorization", bearer(viewer.getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> refs = JsonPath.read(showcase, "$.userInfos[*].userRef");
        assertThat(refs).containsExactly("1", "2", "3");
        assertThat(JsonPath.<List<String>>read(showcase, "$.scenes[?(@.startsAt =~ /.*T09:00.*/)].with[0]")).containsExactly("2");
    }

    @Test
    @DisplayName("대상이 그날 시뮬레이션되지 않았거나 그 구역 트랙이 없으면 404 WORLD_TRACK_NOT_FOUND")
    void showcase_sector_not_found() throws Exception {
        // given: 오늘 트랙은 있지만 어제는 대상의 장면이 없다
        setUpWorld();
        saveWorldTrack(WorldTrack.PUBLIC_VIEWER_USER_ID, """
                {"poolNumber": "7", "date": "%s", "sectorCode": "S034", "layoutVersion": "sha256:aa", "viewerUserId": null,
                 "actors": [], "talks": [], "gestures": [], "objects": [], "events": [], "items": []}
                """.formatted(today));
        worldTrackRepository.upsert(POOL_NUMBER, today.minusDays(1), SECTOR_CODE, WorldTrack.PUBLIC_VIEWER_USER_ID, gzip("""
                {"poolNumber": "7", "date": "%s", "sectorCode": "S034", "layoutVersion": "sha256:aa", "viewerUserId": null,
                 "actors": [], "talks": [], "gestures": [], "objects": [], "events": [], "items": []}
                """.formatted(today.minusDays(1))));

        // when & then: 오늘은 200, 어제는 404, 트랙 없는 구역도 404
        todaySector(today).andExpect(status().isOk());
        todaySector(today.minusDays(1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.WORLD_TRACK_NOT_FOUND.name()));
        mockMvc.perform(get("/api/v1/showcases/today/sectors/{sectorCode}", "S099").header("Authorization", bearer(viewer.getId()))
                        .param("date", today.toString()).param("from", today + "T13:00:00+09:00").param("to", today + "T14:00:00+09:00"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.WORLD_TRACK_NOT_FOUND.name()));
    }

    @Test
    @DisplayName("둘러보기 건물 화면: 인증 헤더가 없으면 401 이고 둘러보기 행을 만들지 않는다")
    void showcase_sector_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 둘러보기 건물 화면 조회
        mockMvc.perform(get("/api/v1/showcases/today/sectors/{sectorCode}", SECTOR_CODE)
                        .param("date", KstTimes.today().toString())
                        .param("from", KstTimes.today() + "T13:00:00+09:00")
                        .param("to", KstTimes.today() + "T14:00:00+09:00"))
                .andExpect(status().isUnauthorized());

        // then: 둘러보기 행이 하나도 생기지 않았다
        assertThat(showcaseRepository.findAll()).isEmpty();
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
                user.getId(), today, "v1", "학교 정문", "SCHOOL_GATE",
                LocalDateTime.of(today, java.time.LocalTime.of(9, 0)),
                LocalDateTime.of(today, java.time.LocalTime.of(9, 40)),
                narration, "아슬아슬했다."));
    }

    private void setUpWorld() {
        today = KstTimes.today();
        viewer = saveUser();
        target = saveSeedParticipant();
        userRepository.assignPoolNumber(target.getId(), POOL_NUMBER);
        partner = saveUserInPool();
        outsider = saveUserInPool();
        target = userRepository.findById(target.getId()).orElseThrow();
        saveTargetSceneWith(partner, 9);
    }

    private ResultActions todaySector(LocalDate date) throws Exception {
        return mockMvc.perform(get("/api/v1/showcases/today/sectors/{sectorCode}", SECTOR_CODE)
                .header("Authorization", bearer(viewer.getId()))
                .param("date", date.toString())
                .param("from", date + "T13:00:00+09:00")
                .param("to", date + "T14:00:00+09:00"));
    }

    private void saveTargetSceneWith(User with, int hour) {
        Scene scene = sceneRepository.save(Scene.createAction(target.getId(), today, "v1", "카페", "P0103",
                today.atTime(LocalTime.of(hour, 0)), today.atTime(LocalTime.of(hour, 30)), "커피를 마셨다.", null));
        scenePartnerRepository.save(ScenePartner.create(scene.getId(), with.getId()));
    }

    private User saveUserInPool() {
        User user = saveUser();
        userRepository.assignPoolNumber(user.getId(), POOL_NUMBER);
        flushAndClear();
        return userRepository.findById(user.getId()).orElseThrow();
    }

    private void saveWorldTrack(long viewerUserId, String body) {
        worldTrackRepository.upsert(POOL_NUMBER, today, SECTOR_CODE, viewerUserId, gzip(body));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static byte[] gzip(String body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(body.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }
}
