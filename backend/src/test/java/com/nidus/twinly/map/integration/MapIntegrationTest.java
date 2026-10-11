package com.nidus.twinly.map.integration;

import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.entity.ScenePartner;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.block.entity.Block;
import com.nidus.twinly.block.repository.BlockRepository;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.relationship.entity.Relationship;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.entity.SeasonParticipation;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import com.nidus.twinly.season.repository.SeasonRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MapIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    SeasonRepository seasonRepository;

    @Autowired
    SeasonParticipationRepository seasonParticipationRepository;

    @Autowired
    SceneRepository sceneRepository;

    @Autowired
    ScenePartnerRepository scenePartnerRepository;

    @Autowired
    RelationshipRepository relationshipRepository;

    @Autowired
    BlockRepository blockRepository;

    Season season;
    LocalDateTime now;
    LocalDate today;
    User me;

    @BeforeEach
    void setUp() {
        // given: 활성 시즌과 호출자
        season = seasonRepository.save(Season.create(Instant.now().minusSeconds(86400 * 7), Instant.now().plusSeconds(86400 * 7)));
        now = KstTimes.now();
        today = now.toLocalDate();
        me = saveUser();
    }

    @Test
    @DisplayName("인연·접점 없는 트윈 분류: 어제 이전 인연은 닉네임, 접점 없는 트윈은 누군가의 트윈, 내 미래 장면 동행자와 오늘 생긴 인연은 빠진다")
    void twins_classifies_end_to_end() throws Exception {
        // given: 어제 생긴 인연(내 지난 장면에도 동행), 접점 없는 트윈, 내 오늘 미래 장면 동행자, 오늘 0시에 처음 생긴 인연
        User friend = saveParticipantBeforeToday();
        User stranger = saveParticipantBeforeToday();
        User metLater = saveParticipantBeforeToday();
        User newFriend = saveParticipantBeforeToday();
        saveRelationship(friend, today.minusDays(1).atTime(10, 0));
        saveRelationship(newFriend, today.atStartOfDay());

        Scene myPast = saveAction(me, now.minusHours(2), now.minusHours(1), "P0012");
        scenePartnerRepository.save(ScenePartner.create(myPast.getId(), friend.getId()));
        Scene myFuture = sceneRepository.save(Scene.createDialogue(me.getId(), today, "v1", "카페", "P0158",
                now.plusMinutes(1), now.plusMinutes(2), "[]"));
        scenePartnerRepository.save(ScenePartner.create(myFuture.getId(), metLater.getId()));

        saveAction(friend, now.minusHours(2), now.minusHours(1), "P0012");
        saveAction(metLater, now.minusHours(2), now.minusHours(1), "P0012");
        saveAction(newFriend, now.minusHours(2), now.minusHours(1), "P0012");
        saveAction(stranger, now.minusHours(3), now.minusHours(2), "P0012");
        sceneRepository.save(Scene.createMove(stranger.getId(), now.minusHours(2).toLocalDate(), "v1",
                "도서관", "P0012", "카페", "P0158", now.minusHours(2), now.minusHours(2).plusMinutes(10),
                "walk", "sha256:89af", "카페로 걸어갔다", null));

        // when: 호출자의 지도 트윈 조회
        mockMvc.perform(get("/api/v1/map/twins")
                        .header("Authorization", bearer(me.getId())))
                // then: 인연이 먼저, 접점 없는 트윈이 다음, 나머지는 없다
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverNow").value(endsWith("+09:00")))
                .andExpect(jsonPath("$.twins", hasSize(2)))
                .andExpect(jsonPath("$.twins[0].userId").value(friend.getId().toString()))
                .andExpect(jsonPath("$.twins[0].displayName").value(friend.getNickname()))
                .andExpect(jsonPath("$.twins[1].userId").value(stranger.getId().toString()))
                .andExpect(jsonPath("$.twins[1].displayName").value("누군가의 트윈"))
                // then: 접점 없는 트윈의 장면은 시작 순서대로 action·move 이고 문장·동행자는 없다
                .andExpect(jsonPath("$.twins[1].scenes", hasSize(2)))
                .andExpect(jsonPath("$.twins[1].scenes[0].type").value("action"))
                .andExpect(jsonPath("$.twins[1].scenes[0].startsAt").value(endsWith("+09:00")))
                .andExpect(jsonPath("$.twins[1].scenes[0].fromPlaceCode").value(nullValue()))
                .andExpect(jsonPath("$.twins[1].scenes[0].travelMode").value(nullValue()))
                .andExpect(jsonPath("$.twins[1].scenes[1].type").value("move"))
                .andExpect(jsonPath("$.twins[1].scenes[1].fromPlaceCode").value("P0012"))
                .andExpect(jsonPath("$.twins[1].scenes[1].placeCode").value("P0158"))
                .andExpect(jsonPath("$.twins[1].scenes[1].travelMode").value("walk"))
                .andExpect(jsonPath("$.twins[1].scenes[1].narration").doesNotExist());
    }

    @Test
    @DisplayName("제외 조건: 오늘 참여자, 양방향 차단, 탈퇴자, 시드 유저, 24시간 전에 끝난 장면만 있는 사람은 빠지고 경계 안쪽 장면만 남는다")
    void twins_excludes_end_to_end() throws Exception {
        // given: 경계 바로 안쪽에서 끝난 장면과 경계 밖 장면을 모두 가진 접점 없는 트윈
        User visible = saveParticipantBeforeToday();
        saveAction(visible, now.minusHours(25), now.minusHours(24), "P0001");
        saveAction(visible, now.minusHours(24).minusMinutes(30), now.minusHours(24).plusMinutes(5), "P0012");

        // given: 각 제외 조건에 해당하는 참여자들 (모두 범위 안 장면이 있다)
        User joinedToday = saveParticipant(Instant.now());
        User blockedByMe = saveParticipantBeforeToday();
        User blockedMe = saveParticipantBeforeToday();
        User withdrawn = saveParticipantBeforeToday();
        blockRepository.save(Block.create(me.getId(), blockedByMe.getId()));
        blockRepository.save(Block.create(blockedMe.getId(), me.getId()));
        withdrawn.requestWithdrawal(Duration.ofDays(14));
        userRepository.save(withdrawn);
        for (User user : new User[]{joinedToday, blockedByMe, blockedMe, withdrawn}) {
            saveAction(user, now.minusHours(1), now, "P0012");
        }

        // given: 범위 안 장면이 있는 시드 참여자(이용권 없이 시더가 참여시킨 유저)
        User seed = saveParticipantBeforeToday();
        userRepository.markSeed(List.of(seed.getId()));
        saveAction(seed, now.minusHours(1), now, "P0012");

        // given: 24시간 전에 끝난 장면만 있는 참여자
        User stale = saveParticipantBeforeToday();
        saveAction(stale, now.minusHours(25), now.minusHours(24), "P0012");

        // when: 호출자의 지도 트윈 조회
        mockMvc.perform(get("/api/v1/map/twins")
                        .header("Authorization", bearer(me.getId())))
                // then: 경계 안쪽 장면을 가진 한 명만, 그 장면 하나만 남는다
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twins[*].userId", containsInAnyOrder(visible.getId().toString())))
                .andExpect(jsonPath("$.twins[0].scenes", hasSize(1)))
                .andExpect(jsonPath("$.twins[0].scenes[0].placeCode").value("P0012"));
    }

    @Test
    @DisplayName("보여 줄 트윈이 없으면 200 과 빈 twins 를 반환한다")
    void twins_empty_end_to_end() throws Exception {
        // when: 다른 참여자가 없는 상태로 조회
        mockMvc.perform(get("/api/v1/map/twins")
                        .header("Authorization", bearer(me.getId())))
                // then: 오류가 아니라 200 + twins: []
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twins", hasSize(0)));
    }

    @Test
    @DisplayName("인증 헤더가 없으면 401을 반환한다")
    void twins_without_auth_returns_401() throws Exception {
        // when & then: 인증 없이 호출하면 401
        mockMvc.perform(get("/api/v1/map/twins"))
                .andExpect(status().isUnauthorized());
    }

    private User saveParticipantBeforeToday() {
        return saveParticipant(KstTimes.toInstant(today.atStartOfDay()).minusSeconds(3600));
    }

    private User saveParticipant(Instant participatedInAt) {
        User user = saveUser();
        SeasonParticipation participation = SeasonParticipation.create(user.getId(), season.getId());
        ReflectionTestUtils.setField(participation, "participatedInAt", participatedInAt);
        seasonParticipationRepository.save(participation);
        return user;
    }

    private void saveRelationship(User partner, LocalDateTime updateTime) {
        relationshipRepository.save(Relationship.create(me.getId(), updateTime.toLocalDate(), "v1",
                partner.getId(), 0, "{}", updateTime, Instant.now()));
    }

    private Scene saveAction(User user, LocalDateTime startsAt, LocalDateTime endsAt, String placeCode) {
        return sceneRepository.save(Scene.createAction(user.getId(), startsAt.toLocalDate(), "v1", "장소", placeCode,
                startsAt, endsAt, "걸었다", null));
    }
}
