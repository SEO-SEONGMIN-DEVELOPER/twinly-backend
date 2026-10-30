package com.nidus.twinly.season.integration;

import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.entity.ScenePartner;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.block.entity.Block;
import com.nidus.twinly.block.repository.BlockRepository;
import com.nidus.twinly.people.domain.TwinViewKind;
import com.nidus.twinly.people.entity.TwinView;
import com.nidus.twinly.people.repository.TwinViewRepository;
import com.nidus.twinly.relationship.entity.Relationship;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.season.entity.SeasonParticipation;
import com.nidus.twinly.season.entity.SeasonRecap;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import com.nidus.twinly.season.repository.SeasonRecapRepository;
import com.nidus.twinly.season.service.SeasonRecapService;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeasonRecapIntegrationTest extends AbstractIntegrationTest {

    private static final long OLD_SEASON_ID = 6L;
    private static final long RECAP_SEASON_ID = 7L;
    private static final long CURRENT_SEASON_ID = 8L;
    private static final Instant STARTED_AT = Instant.parse("2026-09-30T15:00:00Z");
    private static final Instant ENDED_AT = Instant.parse("2026-10-31T14:59:59Z");
    private static final Instant RESET_AT = Instant.parse("2026-10-20T15:00:00Z");
    private static final Instant TEN_AM_ON_RESET_DAY = Instant.parse("2026-10-21T01:00:00Z");

    @Autowired
    SeasonRecapService seasonRecapService;

    @Autowired
    SeasonRecapRepository seasonRecapRepository;

    @Autowired
    SeasonParticipationRepository seasonParticipationRepository;

    @Autowired
    SceneRepository sceneRepository;

    @Autowired
    ScenePartnerRepository scenePartnerRepository;

    @Autowired
    RelationshipRepository relationshipRepository;

    @Autowired
    TwinViewRepository twinViewRepository;

    @Autowired
    BlockRepository blockRepository;

    @PersistenceContext
    EntityManager entityManager;

    User me;
    User friend;
    User acquaintance;

    @BeforeEach
    void setUp() {
        saveSeason(OLD_SEASON_ID, STARTED_AT.minusSeconds(31L * 86400), STARTED_AT, false, STARTED_AT.minusSeconds(31L * 86400));
        saveSeason(RECAP_SEASON_ID, STARTED_AT, ENDED_AT, false, STARTED_AT);
        saveSeason(CURRENT_SEASON_ID, RESET_AT, RESET_AT.plusSeconds(30L * 86400), true, RESET_AT);

        me = saveUser();
        friend = saveUser();
        acquaintance = saveUser();
        participate(me, friend, acquaintance);

        // 나: 10-02 카페에서 친구·지인과 30분 대화(내 말풍선 2개), 10-03 카페에서 친구와 60분 행동
        Scene dialogue = sceneRepository.save(Scene.createDialogue(me.getId(), LocalDate.parse("2026-10-02"), "v2", "학생 거리 카페", "P0123",
                LocalDateTime.parse("2026-10-02T10:00:00"), LocalDateTime.parse("2026-10-02T10:30:00"),
                lines(me.getId(), friend.getId())));
        Scene action = sceneRepository.save(Scene.createAction(me.getId(), LocalDate.parse("2026-10-03"), "v2", "학생 거리 카페", "P0123",
                LocalDateTime.parse("2026-10-03T10:00:00"), LocalDateTime.parse("2026-10-03T11:00:00"), "커피를 마신다", "좋다"));
        scenePartnerRepository.saveAll(List.of(
                ScenePartner.create(dialogue.getId(), friend.getId()),
                ScenePartner.create(dialogue.getId(), acquaintance.getId()),
                ScenePartner.create(action.getId(), friend.getId())));

        // 리셋 뒤(집계 기간 밖)의 장면은 세지 않는다
        sceneRepository.save(Scene.createAction(me.getId(), LocalDate.parse("2026-10-22"), "v2", "도서관", "P0300",
                LocalDateTime.parse("2026-10-22T10:00:00"), LocalDateTime.parse("2026-10-22T13:00:00"), "공부", "피곤"));

        // 친구·지인도 각자 장면이 있어 대상자다 (친구 120분 행동)
        sceneRepository.save(Scene.createAction(friend.getId(), LocalDate.parse("2026-10-05"), "v2", "공원", "P0200",
                LocalDateTime.parse("2026-10-05T09:00:00"), LocalDateTime.parse("2026-10-05T11:00:00"), "산책", "상쾌"));
        sceneRepository.save(Scene.createAction(acquaintance.getId(), LocalDate.parse("2026-10-05"), "v2", "공원", "P0200",
                LocalDateTime.parse("2026-10-05T09:00:00"), LocalDateTime.parse("2026-10-05T09:10:00"), "산책", "상쾌"));

        // 집계 끝 친밀도: 친구 40, 지인 10
        relationshipRepository.save(Relationship.create(me.getId(), LocalDate.parse("2026-10-03"), "v2", friend.getId(), 40, "model",
                LocalDateTime.parse("2026-10-03T11:00:00"), null));
        relationshipRepository.save(Relationship.create(me.getId(), LocalDate.parse("2026-10-02"), "v2", acquaintance.getId(), 10, "model",
                LocalDateTime.parse("2026-10-02T10:30:00"), null));

        // 친구(KST 20시 두 번 + 19시대 열 번)·지인(KST 21시 한 번)이 내 프로필을 봄
        saveView(me, friend, "2026-10-04T11:00:00Z");
        saveView(me, friend, "2026-10-05T11:00:00Z");
        saveView(me, acquaintance, "2026-10-05T12:00:00Z");
        for (int i = 0; i < 10; i++) {
            saveView(me, friend, "2026-10-06T10:0" + i + ":00Z");
        }
    }

    @Test
    @DisplayName("리셋 당일 10시의 준비 작업이 대상자 전원의 리캡을 만들고, 최근 리캡·메인·알림 목록이 그 리캡을 가리킨다")
    void prepare_then_read_latest_recap_end_to_end() throws Exception {
        // given: 시드 계정과 집계 기간 중 탈퇴 신청한 계정은 장면이 있어도 대상이 아니다
        User seed = saveUser();
        ReflectionTestUtils.setField(seed, "isSeed", true);
        userRepository.saveAndFlush(seed);
        User withdrawn = saveUser();
        ReflectionTestUtils.setField(withdrawn, "withdrawalRequestedAt", Instant.parse("2026-10-10T00:00:00Z"));
        userRepository.saveAndFlush(withdrawn);
        participate(seed, withdrawn);
        sceneRepository.save(Scene.createAction(seed.getId(), LocalDate.parse("2026-10-05"), "v2", "공원", "P0200",
                LocalDateTime.parse("2026-10-05T09:00:00"), LocalDateTime.parse("2026-10-05T10:00:00"), "산책", "상쾌"));
        sceneRepository.save(Scene.createAction(withdrawn.getId(), LocalDate.parse("2026-10-05"), "v2", "공원", "P0200",
                LocalDateTime.parse("2026-10-05T09:00:00"), LocalDateTime.parse("2026-10-05T10:00:00"), "산책", "상쾌"));

        // when: 리셋 당일 오전 10시에 준비 작업 실행
        seasonRecapService.prepare(TEN_AM_ON_RESET_DAY);

        // then: 대상자 3명만 리캡이 생기고 시즌에 준비·알림 시각이 남는다
        assertThat(seasonRecapRepository.findAll()).extracting(SeasonRecap::getUserId)
                .containsExactlyInAnyOrder(me.getId(), friend.getId(), acquaintance.getId());

        // then: 최근 리캡 — 집계 끝은 일찍 한 리셋 시각, 리셋 뒤 장면은 빠짐
        mockMvc.perform(get("/api/v1/season/recaps/latest").header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seasonId").value("7"))
                .andExpect(jsonPath("$.startedAt").value("2026-10-01T00:00:00+09:00"))
                .andExpect(jsonPath("$.endedAt").value("2026-10-21T00:00:00+09:00"))
                .andExpect(jsonPath("$.population").value(3))
                .andExpect(jsonPath("$.together.days").value(3))
                .andExpect(jsonPath("$.activity.dialogue.minutes").value(30))
                .andExpect(jsonPath("$.activity.dialogue.topPercent").value(34))
                .andExpect(jsonPath("$.activity.action.minutes").value(60))
                .andExpect(jsonPath("$.activity.action.topPercent").value(67))
                .andExpect(jsonPath("$.activity.move.minutes").value(0))
                .andExpect(jsonPath("$.activity.move.topPercent").value(nullValue()))
                .andExpect(jsonPath("$.bubbles.count").value(2))
                .andExpect(jsonPath("$.places[0].placeCode").value("P0123"))
                .andExpect(jsonPath("$.places[0].visitCount").value(1))
                .andExpect(jsonPath("$.places[0].minutes").value(90))
                .andExpect(jsonPath("$.talked.count").value(2))
                .andExpect(jsonPath("$.friends.count").value(1))
                .andExpect(jsonPath("$.mostMet[0].userId").value(friend.getId().toString()))
                .andExpect(jsonPath("$.mostMet[0].userName").value(friend.displayNickname()))
                .andExpect(jsonPath("$.mostMet[0].days").value(2))
                .andExpect(jsonPath("$.mostMet[0].sceneCount").value(2))
                .andExpect(jsonPath("$.closest[0].userId").value(friend.getId().toString()))
                .andExpect(jsonPath("$.closest[0].twinIntimacy").value(40))
                .andExpect(jsonPath("$.closest[1].twinIntimacy").value(10))
                .andExpect(jsonPath("$.bestMatches").isEmpty())
                .andExpect(jsonPath("$.profileViewers.count").value(2))
                .andExpect(jsonPath("$.profileViewers.topPercent").value(34))
                .andExpect(jsonPath("$.activityType").value(nullValue()));

        // then: 친구는 KST 저녁에 12번 프로필을 봐서 활동 유형이 저녁
        mockMvc.perform(get("/api/v1/season/recaps/latest").header("Authorization", bearer(friend.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activityType.type").value("evening"))
                .andExpect(jsonPath("$.activityType.counts.evening").value(12))
                .andExpect(jsonPath("$.activityType.counts.morning").value(0));

        // then: 메인 탭이 7번 시즌 리캡을 알려 준다
        mockMvc.perform(get("/api/v1/main").header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.season.seasonId").value("8"))
                .andExpect(jsonPath("$.latestRecap.seasonId").value("7"));

        // then: 알림 목록에 리캡 알림이 seasonRecap 대상(시즌 7)으로 남는다
        mockMvc.perform(get("/api/v1/me/app-notifications/feeds").header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appNotificationFeeds[0].type").value("seasonRecap"))
                .andExpect(jsonPath("$.appNotificationFeeds[0].title").value("10월 평행우주 리캡이 도착했어요"))
                .andExpect(jsonPath("$.appNotificationFeeds[0].target.kind").value("seasonRecap"))
                .andExpect(jsonPath("$.appNotificationFeeds[0].target.seasonId").value("7"));
    }

    @Test
    @DisplayName("리캡 생성 뒤 차단한 상대는 응답에서 빠지고 다음 순위로 채우지 않는다")
    void latest_recap_hides_blocked_partner() throws Exception {
        // given: 리캡 생성 뒤 내가 친구를 차단
        seasonRecapService.prepare(TEN_AM_ON_RESET_DAY);
        blockRepository.save(Block.create(me.getId(), friend.getId()));

        // when & then: 친구가 빠지고 지인만 남음, 숫자는 그대로
        mockMvc.perform(get("/api/v1/season/recaps/latest").header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mostMet.length()").value(1))
                .andExpect(jsonPath("$.mostMet[0].userId").value(acquaintance.getId().toString()))
                .andExpect(jsonPath("$.closest.length()").value(1))
                .andExpect(jsonPath("$.talked.count").value(2));
    }

    @Test
    @DisplayName("리셋 당일 10시 전에는 리캡만 만들고 알림은 만들지 않는다")
    void prepare_before_ten_creates_recaps_without_feeds() throws Exception {
        // when: 리셋 직후(KST 00:05) 준비 작업 실행
        seasonRecapService.prepare(RESET_AT.plusSeconds(300));

        // then: 리캡은 조회되지만 알림 목록은 비어 있음
        mockMvc.perform(get("/api/v1/season/recaps/latest").header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me/app-notifications/feeds").header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appNotificationFeeds").isEmpty());
    }

    @Test
    @DisplayName("다음 리셋이 일어나면 지난 리캡은 제공이 끝나 404·null이 되고, 다음 준비 작업에서 지워진다")
    void next_reset_ends_serving_and_deletes() throws Exception {
        // given: 7번 리캡을 만든 뒤 9번 시즌으로 리셋
        seasonRecapService.prepare(TEN_AM_ON_RESET_DAY);
        entityManager.createNativeQuery("UPDATE seasons SET is_active = 0 WHERE id = ?1").setParameter(1, CURRENT_SEASON_ID).executeUpdate();
        saveSeason(9L, RESET_AT.plusSeconds(30L * 86400), RESET_AT.plusSeconds(60L * 86400), true, RESET_AT.plusSeconds(30L * 86400));

        // when & then: 바로 제공이 끝남
        mockMvc.perform(get("/api/v1/season/recaps/latest").header("Authorization", bearer(me.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEASON_RECAP_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/main").header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestRecap").value(nullValue()));

        // when: 다음 준비 작업
        seasonRecapService.prepare(RESET_AT.plusSeconds(30L * 86400 + 60));

        // then: 7번 리캡이 지워짐
        assertThat(seasonRecapRepository.findAll()).extracting(SeasonRecap::getSeasonId).doesNotContain(RECAP_SEASON_ID);
    }

    @Test
    @DisplayName("리캡을 받지 않은 사람은 404, 인증이 없으면 401이다")
    void latest_recap_not_found_or_unauthorized() throws Exception {
        // given: 리캡 생성 + 대상이 아닌 새 유저
        seasonRecapService.prepare(TEN_AM_ON_RESET_DAY);
        User stranger = saveUser();

        // when & then
        mockMvc.perform(get("/api/v1/season/recaps/latest").header("Authorization", bearer(stranger.getId())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/season/recaps/latest"))
                .andExpect(status().isUnauthorized());
    }

    private void saveSeason(long id, Instant startedAt, Instant endedAt, boolean active, Instant createdAt) {
        entityManager.createNativeQuery(
                        "INSERT INTO seasons (id, started_at, ended_at, is_active, created_at, activated_at) VALUES (?1, ?2, ?3, ?4, ?5, ?5)")
                .setParameter(1, id)
                .setParameter(2, Timestamp.from(startedAt))
                .setParameter(3, Timestamp.from(endedAt))
                .setParameter(4, active)
                .setParameter(5, Timestamp.from(createdAt))
                .executeUpdate();

        if (id == OLD_SEASON_ID) {
            entityManager.createNativeQuery("UPDATE seasons SET recap_created_at = ?1, recap_notified_at = ?1 WHERE id = ?2")
                    .setParameter(1, Timestamp.from(createdAt))
                    .setParameter(2, id)
                    .executeUpdate();
        }
    }

    private void participate(User... users) {
        for (User user : users) {
            SeasonParticipation participation = SeasonParticipation.create(user.getId(), RECAP_SEASON_ID);
            ReflectionTestUtils.setField(participation, "participatedInAt", STARTED_AT);
            seasonParticipationRepository.save(participation);
        }
    }

    private void saveView(User target, User viewer, String viewedAt) {
        TwinView view = TwinView.create(target.getId(), viewer.getId(), TwinViewKind.PROFILE);
        ReflectionTestUtils.setField(view, "viewedAt", Instant.parse(viewedAt));
        twinViewRepository.save(view);
    }

    private String lines(Long myId, Long partnerId) {
        return """
                [{"t":"bubble","userId":%d,"action":null,"text":"안녕","occursAt":"2026-10-02T10:01:00"},
                 {"t":"bubble","userId":%d,"action":null,"text":"응","occursAt":"2026-10-02T10:02:00"},
                 {"t":"bubble","userId":%d,"action":null,"text":"뭐해","occursAt":"2026-10-02T10:03:00"}]
                """.formatted(myId, partnerId, myId);
    }
}
