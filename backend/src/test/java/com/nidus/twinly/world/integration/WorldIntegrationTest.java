package com.nidus.twinly.world.integration;

import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.block.entity.Block;
import com.nidus.twinly.block.repository.BlockRepository;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.relationship.entity.Relationship;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
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
import org.springframework.test.web.servlet.ResultActions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.zip.GZIPOutputStream;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorldIntegrationTest extends AbstractIntegrationTest {

    private static final int POOL_NUMBER = 7;
    private static final LocalDate DATE = LocalDate.of(2026, 10, 16);
    private static final String SECTOR_CODE = "S034";
    private static final long CURRENT_SEASON_ID = 1L;
    private static final String FROM = "2026-10-16T13:00:00+09:00";
    private static final String TO = "2026-10-16T14:00:00+09:00";
    // 엑스트라 번호는 70자리가 넘을 수 있어 숫자로 읽으면 끝자리가 뭉개진다
    private static final String LONG_EXTRA_REF = "extra:" + "1234567890".repeat(8);
    private static final String ANONYMOUS = "누군가의 트윈";

    @Autowired
    WorldTrackRepository worldTrackRepository;

    @Autowired
    SeasonParticipationRepository seasonParticipationRepository;

    @Autowired
    RelationshipRepository relationshipRepository;

    @Autowired
    BlockRepository blockRepository;

    @Autowired
    SceneRepository sceneRepository;

    @PersistenceContext
    EntityManager entityManager;

    User me;

    @BeforeEach
    void setUp() {
        entityManager.createNativeQuery("""
                        INSERT INTO seasons (id, started_at, ended_at, is_active, created_at)
                        VALUES (:id, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 1, UTC_TIMESTAMP(6))
                        """)
                .setParameter("id", CURRENT_SEASON_ID)
                .executeUpdate();
        me = participantInPool();
    }

    @Test
    @DisplayName("창과 겹치는 구간을 자르지 않고 주고, 사물은 창 시작 상태와 창 안 변화만, 시각에는 +09:00 을 붙인다")
    void sector_filters_public_track_by_window() throws Exception {
        // given: 창(13~14시) 안팎의 구간·말·몸짓·사물 변화·사건·음식이 든 공용 트랙
        User stranger = userInPool();
        saveTrack(WorldTrack.PUBLIC_VIEWER_USER_ID, """
                {"poolNumber": "7", "date": "2026-10-16", "sectorCode": "S034", "layoutVersion": "sha256:aa", "viewerUserId": null,
                 "weather": "rain",
                 "actors": [
                   {"ref": "user:%1$d", "segments": [
                     {"start": "2026-10-16T11:00:00", "end": "2026-10-16T12:00:00", "pose": "sit"},
                     {"start": "2026-10-16T12:50:00", "end": "2026-10-16T13:10:00.833", "pose": "walk", "path": [[4,18],[12,9]]}]},
                   {"ref": "user:%2$d", "segments": [{"start": "2026-10-16T14:00:00", "end": "2026-10-16T14:30:00", "pose": "stand"}]},
                   {"ref": "%3$s", "mood": "happy", "segments": [{"start": "2026-10-16T13:10:00.333", "end": "2026-10-16T13:15:00.667", "pose": "sit"}]}
                 ],
                 "talks": [
                   {"start": "2026-10-16T13:20:00", "end": "2026-10-16T13:20:04", "placeCode": "P0103", "members": ["npc:npc082", "user:%1$d"]},
                   {"start": "2026-10-16T12:00:00", "end": "2026-10-16T12:10:00", "placeCode": "P0103", "members": ["npc:npc082", "user:%1$d"]}
                 ],
                 "gestures": [
                   {"ref": "user:%1$d", "start": "2026-10-16T13:00:00", "end": "2026-10-16T13:00:00", "motion": "wave"},
                   {"ref": "user:%1$d", "start": "2026-10-16T14:00:00", "end": "2026-10-16T14:00:00", "motion": "wave"}
                 ],
                 "objects": [
                   {"objectId": "P0103-o3", "kind": "창문", "initial": "닫힘", "changes": [
                     {"at": "2026-10-16T13:41:00", "state": "닫힘", "by": "card:C048"},
                     {"at": "2026-10-16T12:30:00", "state": "열림", "by": "user:%1$d"},
                     {"at": "2026-10-16T14:10:00", "state": "열림", "by": "user:%1$d"}]},
                   {"objectId": "P0103-o4", "kind": "조명", "initial": "꺼짐"}
                 ],
                 "events": [
                   {"cardId": "C048", "placeCode": "P0103", "start": "2026-10-16T14:02:00", "end": "2026-10-16T14:02:00", "extras": []},
                   {"cardId": "C011", "placeCode": "P0103", "start": "2026-10-16T13:30:00", "end": "2026-10-16T13:30:00", "extras": ["%3$s"]}
                 ],
                 "items": [
                   {"owner": "user:%1$d", "item": "카페라떼", "placeCode": "P0103", "tile": [12,6], "start": "2026-10-16T13:20:16.667", "end": "2026-10-16T14:30:00"},
                   {"owner": "user:%1$d", "item": "물", "placeCode": "P0103", "tile": [12,6], "start": "2026-10-16T10:00:00", "end": "2026-10-16T11:00:00"}
                 ]}
                """.formatted(me.getId(), stranger.getId(), LONG_EXTRA_REF));

        // when & then: 13~14시 창 조회 시 겹치는 원소만 원래 모양 그대로, 시각에는 +09:00
        sector(FROM, TO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poolNumber").value("7"))
                .andExpect(jsonPath("$.date").value("2026-10-16"))
                .andExpect(jsonPath("$.sectorCode").value(SECTOR_CODE))
                .andExpect(jsonPath("$.layoutVersion").value("sha256:aa"))
                .andExpect(jsonPath("$.focusRef").value("user:" + me.getId()))
                .andExpect(jsonPath("$.serverNow").value(containsString("+09:00")))
                .andExpect(jsonPath("$.weather").doesNotExist())
                .andExpect(jsonPath("$.actors", hasSize(2)))
                .andExpect(jsonPath("$.actors[0].segments", hasSize(1)))
                .andExpect(jsonPath("$.actors[0].segments[0].start").value("2026-10-16T12:50:00+09:00"))
                .andExpect(jsonPath("$.actors[0].segments[0].end").value("2026-10-16T13:10:00.833+09:00"))
                .andExpect(jsonPath("$.actors[0].segments[0].path[1][1]").value(9))
                .andExpect(jsonPath("$.actors[1].ref").value(LONG_EXTRA_REF))
                .andExpect(jsonPath("$.actors[1].mood").value("happy"))
                .andExpect(jsonPath("$.actors[1].displayName").doesNotExist())
                .andExpect(jsonPath("$.talks", hasSize(1)))
                .andExpect(jsonPath("$.talks[0].end").value("2026-10-16T13:20:04+09:00"))
                .andExpect(jsonPath("$.gestures", hasSize(1)))
                .andExpect(jsonPath("$.gestures[0].start").value("2026-10-16T13:00:00+09:00"))
                .andExpect(jsonPath("$.objects", hasSize(2)))
                .andExpect(jsonPath("$.objects[0].initial").value("열림"))
                .andExpect(jsonPath("$.objects[0].changes", hasSize(1)))
                .andExpect(jsonPath("$.objects[0].changes[0].at").value("2026-10-16T13:41:00+09:00"))
                .andExpect(jsonPath("$.objects[1].initial").value("꺼짐"))
                .andExpect(jsonPath("$.objects[1].changes").doesNotExist())
                .andExpect(jsonPath("$.events", hasSize(1)))
                .andExpect(jsonPath("$.events[0].cardId").value("C011"))
                .andExpect(jsonPath("$.events[0].extras[0]").value(LONG_EXTRA_REF))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].start").value("2026-10-16T13:20:16.667+09:00"));
    }

    @Test
    @DisplayName("본인·인연은 닉네임, 회차 중 처음 만난 사람·차단·탈퇴·없는 사용자는 누군가의 트윈이고, 탈퇴·없는 사용자는 gender null")
    void sector_applies_name_and_gender_rules() throws Exception {
        // given: 회차 06:00 전에 만난 인연, 회차 중에 처음 만난 사람, 인연이지만 차단한 사람, 탈퇴한 사람, 없는 사용자
        User friend = userInPool();
        User metToday = userInPool();
        User blocked = userInPool();
        User withdrawn = userInPool();
        relate(friend, LocalDateTime.of(2026, 10, 15, 22, 0));
        relate(metToday, LocalDateTime.of(2026, 10, 16, 9, 0));
        relate(blocked, LocalDateTime.of(2026, 10, 10, 9, 0));
        blockRepository.save(Block.create(blocked.getId(), me.getId()));
        withdrawn.requestWithdrawal(Duration.ofDays(15));
        userRepository.save(withdrawn);
        long unknownUserId = 9_999_999L;
        saveTrack(WorldTrack.PUBLIC_VIEWER_USER_ID, trackWithActors("sha256:aa",
                me.getId(), friend.getId(), metToday.getId(), blocked.getId(), withdrawn.getId(), unknownUserId));
        flushAndClear();

        // when & then: 이름·성별이 규칙대로 붙는다
        sector(FROM, TO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actors[0].displayName").value(me.getNickname()))
                .andExpect(jsonPath("$.actors[0].gender").value("male"))
                .andExpect(jsonPath("$.actors[1].displayName").value(friend.getNickname()))
                .andExpect(jsonPath("$.actors[2].displayName").value(ANONYMOUS))
                .andExpect(jsonPath("$.actors[2].gender").value("male"))
                .andExpect(jsonPath("$.actors[3].displayName").value(ANONYMOUS))
                .andExpect(jsonPath("$.actors[3].gender").value("male"))
                .andExpect(jsonPath("$.actors[4].displayName").value(ANONYMOUS))
                .andExpect(jsonPath("$.actors[4].gender").isEmpty())
                .andExpect(jsonPath("$.actors[5].ref").value("user:" + unknownUserId))
                .andExpect(jsonPath("$.actors[5].displayName").value(ANONYMOUS))
                .andExpect(jsonPath("$.actors[5].gender").isEmpty());
    }

    @Test
    @DisplayName("개인 트랙이 있으면 공용 트랙에 내 몫·더한 사건과 그 엑스트라·말 건 엑스트라·내 사물 변화만 더하고, 남의 개인 트랙은 섞지 않는다")
    void sector_merges_personal_track_of_viewer_only() throws Exception {
        // given: 공용 트랙, 내 개인 트랙(나·직원·사건 엑스트라·말 건 엑스트라·배경 손님), 다른 사람의 개인 트랙
        User other = participantInPool();
        saveTrack(WorldTrack.PUBLIC_VIEWER_USER_ID, """
                {"poolNumber": "7", "date": "2026-10-16", "sectorCode": "S034", "layoutVersion": "sha256:aa", "viewerUserId": null,
                 "actors": [
                   {"ref": "user:%1$d", "segments": [{"start": "2026-10-16T13:00:00", "end": "2026-10-16T13:10:00", "pose": "stand", "from": "public"}]},
                   {"ref": "npc:npc082", "segments": [{"start": "2026-10-16T08:30:00", "end": "2026-10-16T19:00:00", "pose": "stand"}]}
                 ],
                 "talks": [], "gestures": [{"ref": "user:%1$d", "start": "2026-10-16T13:05:00", "end": "2026-10-16T13:05:00", "from": "public"}],
                 "objects": [{"objectId": "P0103-o3", "kind": "창문", "initial": "닫힘", "changes": [{"at": "2026-10-16T13:20:00", "state": "열림", "by": "npc:npc082"}]}],
                 "events": [{"cardId": "C011", "placeCode": "P0103", "start": "2026-10-16T13:30:00", "end": "2026-10-16T13:30:00", "extras": []}],
                 "items": [{"owner": "user:%1$d", "item": "물", "start": "2026-10-16T13:00:00", "end": "2026-10-16T13:30:00", "from": "public"}]}
                """.formatted(me.getId()));
        saveTrack(me.getId(), """
                {"poolNumber": "7", "date": "2026-10-16", "sectorCode": "S034", "layoutVersion": "sha256:aa", "viewerUserId": "%1$d",
                 "actors": [
                   {"ref": "user:%1$d", "segments": [{"start": "2026-10-16T13:05:00", "end": "2026-10-16T13:25:00", "pose": "sit", "from": "personal"}]},
                   {"ref": "npc:npc999", "segments": [{"start": "2026-10-16T08:00:00", "end": "2026-10-16T20:00:00", "pose": "stand"}]},
                   {"ref": "extra:E1", "segments": [{"start": "2026-10-16T13:40:00", "end": "2026-10-16T13:45:00", "pose": "walk"}]},
                   {"ref": "extra:S1", "segments": [{"start": "2026-10-16T13:10:00", "end": "2026-10-16T13:20:00", "pose": "stand"}]},
                   {"ref": "extra:BG", "segments": [{"start": "2026-10-16T13:00:00", "end": "2026-10-16T13:59:00", "pose": "sit"}]}
                 ],
                 "talks": [{"start": "2026-10-16T13:10:00", "end": "2026-10-16T13:11:00", "members": ["extra:S1", "user:%1$d"]}],
                 "gestures": [{"ref": "user:%1$d", "start": "2026-10-16T13:15:00", "end": "2026-10-16T13:15:00", "from": "personal"}],
                 "objects": [{"objectId": "P0103-o3", "kind": "창문", "initial": "닫힘", "changes": [
                   {"at": "2026-10-16T13:50:00", "state": "닫힘", "by": "user:%1$d"},
                   {"at": "2026-10-16T13:55:00", "state": "열림", "by": "card:C077"},
                   {"at": "2026-10-16T13:56:00", "state": "닫힘", "by": "npc:npc999"}]}],
                 "events": [
                   {"cardId": "C011", "placeCode": "P0103", "start": "2026-10-16T13:30:00.000", "end": "2026-10-16T13:30:00", "extras": []},
                   {"cardId": "C077", "placeCode": "P0103", "start": "2026-10-16T13:40:00", "end": "2026-10-16T13:40:00", "extras": ["extra:E1"]}],
                 "items": [{"owner": "user:%1$d", "item": "라떼", "start": "2026-10-16T13:20:00", "end": "2026-10-16T13:50:00", "from": "personal"}]}
                """.formatted(me.getId()));
        saveTrack(other.getId(), """
                {"poolNumber": "7", "date": "2026-10-16", "sectorCode": "S034", "layoutVersion": "sha256:aa", "viewerUserId": "%d",
                 "actors": [{"ref": "extra:SECRET", "segments": [{"start": "2026-10-16T13:00:00", "end": "2026-10-16T13:30:00"}]}],
                 "talks": [], "gestures": [], "objects": [], "events": [], "items": []}
                """.formatted(other.getId()));
        sceneRepository.save(Scene.createDialogue(me.getId(), DATE, "v1", "카페", "P0103",
                LocalDateTime.of(2026, 10, 16, 13, 10), LocalDateTime.of(2026, 10, 16, 13, 20), """
                        [{"t": "narr", "text": "손님: \\"여기요\\"", "occursAt": "2026-10-16T13:10:30", "speaker": "extra:S1"}]
                        """));
        flushAndClear();

        // when & then: 내 몫·더한 사건과 엑스트라·말 건 엑스트라·내 사물 변화만 합쳐지고, 남의 개인 트랙은 없다
        sector(FROM, TO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actors[*].ref", containsInAnyOrder("npc:npc082", "user:" + me.getId(), "extra:E1", "extra:S1")))
                .andExpect(jsonPath("$.actors[?(@.ref == 'user:%d')].segments[0].from".formatted(me.getId())).value("personal"))
                .andExpect(jsonPath("$.talks", hasSize(0)))
                .andExpect(jsonPath("$.gestures[*].from", containsInAnyOrder("personal")))
                .andExpect(jsonPath("$.items[*].from", containsInAnyOrder("personal")))
                .andExpect(jsonPath("$.events[*].cardId", containsInAnyOrder("C011", "C077")))
                .andExpect(jsonPath("$.objects[0].changes[*].at", containsInAnyOrder(
                        "2026-10-16T13:20:00+09:00", "2026-10-16T13:50:00+09:00", "2026-10-16T13:55:00+09:00")))
                .andExpect(content().string(not(containsString("extra:SECRET"))));
    }

    @Test
    @DisplayName("배치도 버전이 다르면 공용 트랙만, 공용 트랙이 없으면 개인 트랙 전체를 준다")
    void sector_uses_public_only_on_layout_mismatch_and_personal_when_no_public() throws Exception {
        // given: 버전이 다른 공용·개인 트랙
        saveTrack(WorldTrack.PUBLIC_VIEWER_USER_ID, trackWithActors("sha256:aa", me.getId()));
        saveTrack(me.getId(), trackWithActors("sha256:bb", me.getId()).replace("\"actors\": [", "\"actors\": [{\"ref\": \"npc:npc999\", \"segments\": [{\"start\": \"2026-10-16T13:00:00\", \"end\": \"2026-10-16T13:30:00\"}]},"));
        flushAndClear();

        // when & then: 공용만
        sector(FROM, TO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layoutVersion").value("sha256:aa"))
                .andExpect(jsonPath("$.actors[*].ref", containsInAnyOrder("user:" + me.getId())));

        // given: 공용 트랙이 없는 다른 구역의 개인 트랙
        worldTrackRepository.upsert(POOL_NUMBER, DATE, "S035", me.getId(), gzip(trackWithActors("sha256:bb", me.getId())
                .replace("\"actors\": [", "\"actors\": [{\"ref\": \"npc:npc999\", \"segments\": [{\"start\": \"2026-10-16T13:00:00\", \"end\": \"2026-10-16T13:30:00\"}]},")));

        // when & then: 개인 트랙의 직원까지 전부
        mockMvc.perform(get("/api/v1/world/sectors/{sectorCode}", "S035").header("Authorization", bearer(me.getId()))
                        .param("date", "2026-10-16").param("from", FROM).param("to", TO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actors[*].ref", containsInAnyOrder("npc:npc999", "user:" + me.getId())));
    }

    @Test
    @DisplayName("창에 아무도 없으면 200 과 빈 목록, 공백으로 풀린 + 도 받는다")
    void sector_returns_empty_lists_when_window_is_empty() throws Exception {
        // given: 창(13~14시)에 아무도 없는 공용 트랙
        saveTrack(WorldTrack.PUBLIC_VIEWER_USER_ID, trackWithActors("sha256:aa"));

        // when & then: from 의 + 가 인코딩되지 않아 공백으로 온 경우
        sector("2026-10-16T13:00:00 09:00", TO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actors", hasSize(0)))
                .andExpect(jsonPath("$.talks", hasSize(0)))
                .andExpect(jsonPath("$.gestures", hasSize(0)))
                .andExpect(jsonPath("$.objects", hasSize(0)))
                .andExpect(jsonPath("$.events", hasSize(0)))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    @DisplayName("창 규칙을 어기면 400, 미참여자는 403, 트랙이 없으면 404")
    void sector_rejects_invalid_requests() throws Exception {
        // given: 공용 트랙 하나
        saveTrack(WorldTrack.PUBLIC_VIEWER_USER_ID, trackWithActors("sha256:aa"));

        // when & then: 정각이 아님, 오프셋 없음, 1시간이 아님, 회차 밖, 다른 오프셋이어도 같은 순간이면 통과
        sector("2026-10-16T13:30:00+09:00", "2026-10-16T14:30:00+09:00").andExpect(status().isBadRequest());
        sector("2026-10-16T13:00:00", "2026-10-16T14:00:00").andExpect(status().isBadRequest());
        sector(FROM, "2026-10-16T15:00:00+09:00").andExpect(status().isBadRequest());
        sector("2026-10-16T05:00:00+09:00", "2026-10-16T06:00:00+09:00").andExpect(status().isBadRequest());
        sector("2026-10-16T04:00:00Z", "2026-10-16T05:00:00Z").andExpect(status().isOk());

        // when & then: 이번 시즌에 참여하지 않은 사용자
        User outsider = userInPool();
        mockMvc.perform(get("/api/v1/world/sectors/{sectorCode}", SECTOR_CODE).header("Authorization", bearer(outsider.getId()))
                        .param("date", "2026-10-16").param("from", FROM).param("to", TO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.WORLD_PARTICIPATION_REQUIRED.name()));

        // when & then: 트랙이 없는 구역·날짜
        mockMvc.perform(get("/api/v1/world/sectors/{sectorCode}", "S099").header("Authorization", bearer(me.getId()))
                        .param("date", "2026-10-16").param("from", FROM).param("to", TO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.WORLD_TRACK_NOT_FOUND.name()));
    }

    @Test
    @DisplayName("인증 헤더가 없으면 401 이다")
    void sector_without_auth_returns_401() throws Exception {
        // given: 공용 트랙 하나
        saveTrack(WorldTrack.PUBLIC_VIEWER_USER_ID, trackWithActors("sha256:aa", me.getId()));

        // when & then: 인증 헤더 없이 조회하면 401
        mockMvc.perform(get("/api/v1/world/sectors/{sectorCode}", SECTOR_CODE)
                        .param("date", "2026-10-16").param("from", FROM).param("to", TO))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions sector(String from, String to) throws Exception {
        return mockMvc.perform(get("/api/v1/world/sectors/{sectorCode}", SECTOR_CODE)
                .header("Authorization", bearer(me.getId()))
                .param("date", "2026-10-16")
                .param("from", from)
                .param("to", to));
    }

    private User participantInPool() {
        User user = userInPool();
        seasonParticipationRepository.upsert(user.getId(), CURRENT_SEASON_ID);
        return user;
    }

    private User userInPool() {
        User user = saveUser();
        userRepository.assignPoolNumber(user.getId(), POOL_NUMBER);
        flushAndClear();
        return userRepository.findById(user.getId()).orElseThrow();
    }

    private void relate(User partner, LocalDateTime firstUpdateTime) {
        relationshipRepository.save(Relationship.create(me.getId(), firstUpdateTime.toLocalDate(), "v1", partner.getId(), 50, "model", firstUpdateTime, null));
    }

    private void saveTrack(long viewerUserId, String body) {
        worldTrackRepository.upsert(POOL_NUMBER, DATE, SECTOR_CODE, viewerUserId, gzip(body));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static String trackWithActors(String layoutVersion, long... userIds) {
        StringBuilder actors = new StringBuilder();
        for (long userId : userIds) {
            if (!actors.isEmpty()) {
                actors.append(',');
            }
            actors.append("""
                    {"ref": "user:%d", "segments": [{"start": "2026-10-16T13:00:00", "end": "2026-10-16T13:30:00", "pose": "stand"}]}""".formatted(userId));
        }
        return """
                {"poolNumber": "7", "date": "2026-10-16", "sectorCode": "S034", "layoutVersion": "%s", "viewerUserId": null,
                 "actors": [%s], "talks": [], "gestures": [], "objects": [], "events": [], "items": []}
                """.formatted(layoutVersion, actors);
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
