package com.nidus.twinly.season.integration;

import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.relationship.entity.Relationship;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.purchase.entity.PoolCounter;
import com.nidus.twinly.purchase.entity.UserEntitlement;
import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.purchase.repository.PoolCounterRepository;
import com.nidus.twinly.purchase.repository.UserEntitlementRepository;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.entity.SeasonParticipation;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import com.nidus.twinly.season.repository.SeasonRepository;
import com.nidus.twinly.season.service.SeasonReservationService;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "admin.api-token=" + SeasonAdminIntegrationTest.ADMIN_TOKEN)
class SeasonAdminIntegrationTest extends AbstractIntegrationTest {

    static final String ADMIN_TOKEN = "admin-integration-token";

    private static final String ADMIN_SEASON_PATH = "/admin/season";
    private static final String RESERVATION_PATH = "/admin/season/reservation";

    @Autowired
    SeasonRepository seasonRepository;

    @Autowired
    SeasonParticipationRepository seasonParticipationRepository;

    @Autowired
    UserEntitlementRepository userEntitlementRepository;

    @Autowired
    PoolCounterRepository poolCounterRepository;

    @Autowired
    RelationshipRepository relationshipRepository;

    @Autowired
    SceneRepository sceneRepository;

    @Autowired
    SeasonReservationService seasonReservationService;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    @DisplayName("시즌 전환: 관리자 토큰으로 호출하면 기존 활성 시즌이 꺼지고 새 시즌 행이 활성으로 생성된다")
    void changeSeason_end_to_end() throws Exception {
        // given: 이미 활성 시즌이 하나 있는 상태
        Season previous = seasonRepository.save(
                Season.create(Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-06-01T00:00:00Z")));

        // when
        var result = mockMvc.perform(post(ADMIN_SEASON_PATH)
                .header("X-Admin-Token", ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"startedAt":"2026-09-01T00:00:00Z","endedAt":"2026-12-01T00:00:00Z"}
                        """));

        // then: 새 시즌 id가 문자열로 응답되고, DB에는 활성 시즌이 새 것 하나만 남는다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.seasonId").isString());

        List<Season> active = seasonRepository.findAllByIsActiveTrue();

        assertThat(active).hasSize(1);
        assertThat(active.getFirst().getStartedAt()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
        assertThat(active.getFirst().getId()).isNotEqualTo(previous.getId());
        assertThat(seasonRepository.findById(previous.getId()).orElseThrow().getIsActive()).isFalse();
    }

    @Test
    @DisplayName("시즌 전환 후 유저 API가 새 시즌을 현재 시즌으로 바라본다")
    void changeSeason_switchesCurrentSeasonForUsers() throws Exception {
        // given: 전환 이후
        mockMvc.perform(post(ADMIN_SEASON_PATH)
                .header("X-Admin-Token", ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"startedAt":"2026-09-01T00:00:00Z","endedAt":"2026-12-01T00:00:00Z"}
                        """));

        Long newSeasonId = seasonRepository.findAllByIsActiveTrue().getFirst().getId();

        // when & then: 유저의 시즌 참가 조회가 새 시즌을 가리킨다
        mockMvc.perform(get("/api/v1/season/participation")
                        .header("Authorization", bearer(saveUser().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentSeasonId").value(String.valueOf(newSeasonId)));
    }

    @Test
    @DisplayName("시즌 전환: simulation_access 가 살아 있는 유저는 새 시즌에 자동 참가되고, 없는 유저는 참가되지 않는다")
    void changeSeason_participatesPaidUsers_endToEnd() throws Exception {
        // given: 결제·필수 약관 동의 유저, 결제만 하고 동의하지 않은 유저, 무료 유저
        User paid = saveUser();
        User paidWithoutConsent = saveUser();
        User free = saveUser();
        userEntitlementRepository.save(UserEntitlement.create(
                paid.getId(), EntitlementReader.SIMULATION_ACCESS, Instant.now().plus(Duration.ofDays(30)), Instant.now()));
        userEntitlementRepository.save(UserEntitlement.create(
                paidWithoutConsent.getId(), EntitlementReader.SIMULATION_ACCESS, Instant.now().plus(Duration.ofDays(30)), Instant.now()));
        agreeRequiredParallelEntryPolicies(paid.getId());

        // when: 시즌 전환
        mockMvc.perform(post(ADMIN_SEASON_PATH)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startedAt":"2026-09-01T00:00:00Z","endedAt":"2026-12-01T00:00:00Z"}
                                """))
                .andExpect(status().isOk());

        // then: 결제하고 필수 약관에 동의한 유저만 새 시즌 참가 행을 갖는다 (재참가 요청 없이 다음 시즌으로 이어진다)
        Long newSeasonId = seasonRepository.findAllByIsActiveTrue().getFirst().getId();

        assertThat(seasonParticipationRepository.findByUserIdAndSeasonId(paid.getId(), newSeasonId))
                .isPresent()
                .get()
                .extracting(SeasonParticipation::getParticipatedInAt)
                .isNotNull();
        assertThat(seasonParticipationRepository.findByUserIdAndSeasonId(paidWithoutConsent.getId(), newSeasonId)).isEmpty();
        assertThat(seasonParticipationRepository.findByUserIdAndSeasonId(free.getId(), newSeasonId)).isEmpty();
    }

    @Test
    @DisplayName("시즌 예약: 현재 시즌 끝 시각으로 예약하면 시각 전에는 그대로고, 시각이 되면 새 시즌으로 전환된다")
    void reservation_activates_at_started_at() throws Exception {
        // given: 활성 시즌이 미래 KST 자정에 끝남
        LocalDate boundary = KstTimes.today().plusDays(1);
        Instant startedAt = KstTimes.toInstant(boundary.atStartOfDay());
        Instant endedAt = KstTimes.toInstant(boundary.plusMonths(1).atStartOfDay());
        Season previous = seasonRepository.save(Season.create(Instant.parse("2026-01-01T00:00:00Z"), startedAt));

        // when: 예약
        mockMvc.perform(post(RESERVATION_PATH)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(period(startedAt, endedAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seasonId").isString());

        // then: 시각 전에는 전환되지 않는다
        seasonReservationService.activateDue(startedAt.minusSeconds(1));
        assertThat(seasonRepository.findFirstByIsActiveTrueOrderByIdDesc().orElseThrow().getId()).isEqualTo(previous.getId());

        // when: 시각 도달
        seasonReservationService.activateDue(startedAt);

        // then: 새 시즌이 활성이고, 예약은 완료 처리돼 다시 전환되지 않는다
        Season current = seasonRepository.findFirstByIsActiveTrueOrderByIdDesc().orElseThrow();
        assertThat(current.getId()).isNotEqualTo(previous.getId());
        assertThat(current.getStartedAt()).isEqualTo(startedAt);
        assertThat(current.getEndedAt()).isEqualTo(endedAt);
        assertThat(seasonRepository.existsByIsActiveFalseAndActivatedAtIsNull()).isFalse();
        assertThat(current.getActivatedAt()).isEqualTo(startedAt);
        assertThat(seasonRepository.findById(previous.getId()).orElseThrow().getIsActive()).isFalse();

        seasonReservationService.activateDue(startedAt.plusSeconds(60));
        assertThat(seasonRepository.findFirstByIsActiveTrueOrderByIdDesc().orElseThrow().getId()).isEqualTo(current.getId());
    }

    @Test
    @DisplayName("시즌 예약: 예약하면 다음 시즌 id 가 생겨 이미 저장된 다음 시즌 회차에 채워지고, 취소하면 다시 비워진다")
    void reservation_links_rows_saved_before_it() throws Exception {
        // given: 활성 시즌과, 시즌 id 없이 먼저 저장된 다음 시즌 회차
        LocalDate boundary = KstTimes.today().plusDays(1);
        Instant startedAt = KstTimes.toInstant(boundary.atStartOfDay());
        Instant endedAt = KstTimes.toInstant(boundary.plusMonths(1).atStartOfDay());
        Season current = seasonRepository.save(Season.create(Instant.parse("2026-01-01T00:00:00Z"), startedAt));
        User me = saveUser();
        User partner = saveUser();
        Relationship next = relationshipRepository.saveAndFlush(Relationship.create(
                me.getId(), boundary, "v1", partner.getId(), 10, "model", boundary.atTime(12, 0), null));
        Relationship before = relationshipRepository.saveAndFlush(Relationship.create(
                me.getId(), boundary.minusDays(1), "v1", partner.getId(), 77, "model", boundary.minusDays(1).atTime(12, 0), null));
        Scene nextScene = sceneRepository.saveAndFlush(Scene.createDialogue(me.getId(), boundary, "v1", "카페", null,
                boundary.atTime(11, 0), boundary.atTime(12, 0), "[]"));

        // when: 예약
        mockMvc.perform(post(RESERVATION_PATH).header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(period(startedAt, endedAt)))
                .andExpect(status().isOk());
        Long reservedId = seasonRepository.findFirstByIsActiveFalseAndActivatedAtIsNullOrderByStartedAtAsc().orElseThrow().getId();
        entityManager.flush();
        entityManager.clear();

        // then: 각 회차가 자기 시즌 id 를 갖고, 활성 시즌은 그대로다
        assertThat(relationshipRepository.findById(next.getId()).orElseThrow().getSeasonId()).isEqualTo(reservedId);
        assertThat(sceneRepository.findById(nextScene.getId()).orElseThrow().getSeasonId()).isEqualTo(reservedId);
        assertThat(relationshipRepository.findById(before.getId()).orElseThrow().getSeasonId()).isEqualTo(current.getId());
        assertThat(seasonRepository.findFirstByIsActiveTrueOrderByIdDesc().orElseThrow().getId()).isEqualTo(current.getId());

        // when: 취소
        mockMvc.perform(delete(RESERVATION_PATH).header("X-Admin-Token", ADMIN_TOKEN)).andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        // then: 다음 시즌 회차만 다시 비워지고 시즌 행은 사라진다
        assertThat(relationshipRepository.findById(next.getId()).orElseThrow().getSeasonId()).isNull();
        assertThat(sceneRepository.findById(nextScene.getId()).orElseThrow().getSeasonId()).isNull();
        assertThat(seasonRepository.findById(reservedId)).isEmpty();
    }

    @Test
    @DisplayName("시즌 예약 실패: 시작이 현재 시즌 끝과 다르면 422, 이미 예약이 있으면 409 다")
    void reservation_rejects_invalid_and_duplicate() throws Exception {
        // given: 활성 시즌이 미래 KST 자정에 끝남
        LocalDate boundary = KstTimes.today().plusDays(1);
        Instant startedAt = KstTimes.toInstant(boundary.atStartOfDay());
        Instant endedAt = KstTimes.toInstant(boundary.plusMonths(1).atStartOfDay());
        seasonRepository.save(Season.create(Instant.parse("2026-01-01T00:00:00Z"), startedAt));

        // when & then: 시작이 하루 어긋남
        mockMvc.perform(post(RESERVATION_PATH)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(period(startedAt.plus(Duration.ofDays(1)), endedAt)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_SEASON_RESERVATION.name()));

        // when & then: 두 번째 예약
        mockMvc.perform(post(RESERVATION_PATH).header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(period(startedAt, endedAt)))
                .andExpect(status().isOk());
        mockMvc.perform(post(RESERVATION_PATH).header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(period(startedAt, endedAt)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.SEASON_RESERVATION_ALREADY_EXISTS.name()));
    }

    @Test
    @DisplayName("시즌 예약 취소: 취소하면 시각이 와도 전환되지 않고, 다시 취소하면 404 다")
    void reservation_cancel() throws Exception {
        // given: 대기 중인 예약
        LocalDate boundary = KstTimes.today().plusDays(1);
        Instant startedAt = KstTimes.toInstant(boundary.atStartOfDay());
        Instant endedAt = KstTimes.toInstant(boundary.plusMonths(1).atStartOfDay());
        Season previous = seasonRepository.save(Season.create(Instant.parse("2026-01-01T00:00:00Z"), startedAt));
        mockMvc.perform(post(RESERVATION_PATH).header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(period(startedAt, endedAt)))
                .andExpect(status().isOk());

        // when: 취소
        mockMvc.perform(delete(RESERVATION_PATH).header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk());

        // then: 시각이 와도 전환되지 않는다
        seasonReservationService.activateDue(startedAt);
        assertThat(seasonRepository.findFirstByIsActiveTrueOrderByIdDesc().orElseThrow().getId()).isEqualTo(previous.getId());

        // and: 다시 취소하면 404
        mockMvc.perform(delete(RESERVATION_PATH).header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.SEASON_RESERVATION_NOT_FOUND.name()));
    }

    @Test
    @DisplayName("즉시 시즌 전환을 하면 대기 중인 예약은 사라져 두 번 전환되지 않는다")
    void changeSeason_clears_pending_reservation() throws Exception {
        // given: 대기 중인 예약
        LocalDate boundary = KstTimes.today().plusDays(1);
        Instant startedAt = KstTimes.toInstant(boundary.atStartOfDay());
        Instant endedAt = KstTimes.toInstant(boundary.plusMonths(1).atStartOfDay());
        seasonRepository.save(Season.create(Instant.parse("2026-01-01T00:00:00Z"), startedAt));
        mockMvc.perform(post(RESERVATION_PATH).header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(period(startedAt, endedAt)))
                .andExpect(status().isOk());

        // when: 즉시 전환
        mockMvc.perform(post(ADMIN_SEASON_PATH).header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(period(startedAt, endedAt)))
                .andExpect(status().isOk());
        Long switchedId = seasonRepository.findFirstByIsActiveTrueOrderByIdDesc().orElseThrow().getId();

        // then: 시각이 와도 추가 전환이 없다
        seasonReservationService.activateDue(startedAt);
        assertThat(seasonRepository.findFirstByIsActiveTrueOrderByIdDesc().orElseThrow().getId()).isEqualTo(switchedId);
        assertThat(seasonRepository.existsByIsActiveFalseAndActivatedAtIsNull()).isFalse();
    }

    @Test
    @DisplayName("풀 재배정: 자동 참가 대상만 13번 풀부터 배정하고 나머지는 비우며 시드와 카운터를 올바르게 처리한다")
    void reassignPools_end_to_end() throws Exception {
        // given: 활성 시즌과 대상 남 2명·여 1명, 결제만 한 유저, 시드 유저
        seasonRepository.save(Season.create(Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z")));
        User male1 = eligibleUser(Gender.MALE, 99);
        User male2 = eligibleUser(Gender.MALE, 99);
        User female = eligibleUser(Gender.FEMALE, 99);
        User paidWithoutConsent = saveUser();
        ReflectionTestUtils.setField(paidWithoutConsent, "poolNumber", 12);
        userEntitlementRepository.save(UserEntitlement.create(
                paidWithoutConsent.getId(), EntitlementReader.SIMULATION_ACCESS, Instant.now().plus(Duration.ofDays(30)), Instant.now()));
        User seed = eligibleUser(Gender.MALE, 3);
        ReflectionTestUtils.setField(seed, "isSeed", true);
        entityManager.flush();

        // when: 재배정
        var result = mockMvc.perform(post("/admin/season/pools/reassign").header("X-Admin-Token", ADMIN_TOKEN));

        // then: 대상 3명이 13번 풀에 들어가고 응답에 성비가 담긴다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedCount").value(3))
                .andExpect(jsonPath("$.pools[0].poolNumber").value(13))
                .andExpect(jsonPath("$.pools[0].maleCount").value(2))
                .andExpect(jsonPath("$.pools[0].femaleCount").value(1));

        entityManager.flush();
        entityManager.clear();

        assertThat(List.of(male1, male2, female))
                .allSatisfy(user -> assertThat(userRepository.findById(user.getId()).orElseThrow().getPoolNumber()).isEqualTo(13));
        assertThat(userRepository.findById(paidWithoutConsent.getId()).orElseThrow().getPoolNumber()).isNull();
        assertThat(userRepository.findById(seed.getId()).orElseThrow().getPoolNumber()).isEqualTo(3);
        assertThat(poolCounterRepository.findById(PoolCounter.SINGLETON_ID).orElseThrow().getAssignedCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("풀 재배정은 시즌당 한 번만 실행되고, 내부·관리자 어느 쪽으로 다시 호출해도 다시 섞지 않는다")
    void reassignPools_runs_once_per_season() throws Exception {
        // given: 활성 시즌과 예약 시즌, 대상 유저
        LocalDate boundary = KstTimes.today().plusDays(1);
        Instant startedAt = KstTimes.toInstant(boundary.atStartOfDay());
        seasonRepository.save(Season.create(Instant.parse("2026-01-01T00:00:00Z"), startedAt));
        Season reserved = seasonRepository.save(Season.reserve(startedAt, KstTimes.toInstant(boundary.plusMonths(1).atStartOfDay())));
        User first = eligibleUser(Gender.MALE, 99);
        entityManager.flush();

        // when: 내부 경로로 첫 호출
        mockMvc.perform(post(internalReassignPath(reserved.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyReassigned").value(false))
                .andExpect(jsonPath("$.assignedCount").value(1));
        entityManager.flush();
        entityManager.clear();
        assertThat(userRepository.findById(first.getId()).orElseThrow().getPoolNumber()).isEqualTo(13);

        // given: 그 뒤 새 대상이 생기고 카운터도 움직임
        User second = eligibleUser(Gender.FEMALE, 77);
        entityManager.flush();
        entityManager.clear();

        // when: 내부 재시도와 관리자 호출
        mockMvc.perform(post(internalReassignPath(reserved.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyReassigned").value(true));
        mockMvc.perform(post("/admin/season/pools/reassign").header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyReassigned").value(true));
        entityManager.flush();
        entityManager.clear();

        // then: 다시 섞이지 않아 나중 유저 번호도 그대로이고, 카운터도 그대로다
        assertThat(userRepository.findById(first.getId()).orElseThrow().getPoolNumber()).isEqualTo(13);
        assertThat(userRepository.findById(second.getId()).orElseThrow().getPoolNumber()).isEqualTo(77);
        assertThat(poolCounterRepository.findById(PoolCounter.SINGLETON_ID).orElseThrow().getAssignedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("다음 시즌이 예약돼 있으면 현재 시즌 재배정은 409, 없는 시즌은 404 다")
    void reassignPools_rejects_wrong_season() throws Exception {
        // given: 활성 시즌과 예약 시즌
        LocalDate boundary = KstTimes.today().plusDays(1);
        Instant startedAt = KstTimes.toInstant(boundary.atStartOfDay());
        Season current = seasonRepository.save(Season.create(Instant.parse("2026-01-01T00:00:00Z"), startedAt));
        Season reserved = seasonRepository.save(Season.reserve(startedAt, KstTimes.toInstant(boundary.plusMonths(1).atStartOfDay())));

        // when & then
        mockMvc.perform(post(internalReassignPath(current.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.SEASON_POOL_REASSIGN_NOT_ALLOWED.name()));
        mockMvc.perform(post(internalReassignPath(reserved.getId() + 1000)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.SEASON_NOT_FOUND.name()));
    }

    @Test
    @DisplayName("관리자 재배정은 예약 시즌이 있으면 그 시즌을 대상으로 한다")
    void admin_reassign_targets_reserved_season() throws Exception {
        // given: 활성 시즌과 예약 시즌, 대상 유저
        LocalDate boundary = KstTimes.today().plusDays(1);
        Instant startedAt = KstTimes.toInstant(boundary.atStartOfDay());
        Season current = seasonRepository.save(Season.create(Instant.parse("2026-01-01T00:00:00Z"), startedAt));
        Season reserved = seasonRepository.save(Season.reserve(startedAt, KstTimes.toInstant(boundary.plusMonths(1).atStartOfDay())));
        eligibleUser(Gender.MALE, 99);
        entityManager.flush();

        // when
        mockMvc.perform(post("/admin/season/pools/reassign").header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyReassigned").value(false));
        entityManager.flush();
        entityManager.clear();

        // then: 예약 시즌에 완료 표시가 남는다
        assertThat(seasonRepository.findById(reserved.getId()).orElseThrow().getPoolsReassignedAt()).isNotNull();
        assertThat(seasonRepository.findById(current.getId()).orElseThrow().getPoolsReassignedAt()).isNull();
    }

    @Test
    @DisplayName("풀 재배정은 관리자 토큰 없이는 호출할 수 없다")
    void reassignPools_requiresAdminToken() throws Exception {
        mockMvc.perform(post("/admin/season/pools/reassign"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));
    }

    @Test
    @DisplayName("시즌 전환 실패: 시작이 종료보다 뒤면 422와 INVALID_SEASON_PERIOD를 반환하고 아무 행도 만들지 않는다")
    void changeSeason_invalidPeriod_returns422() throws Exception {
        long before = seasonRepository.count();

        mockMvc.perform(post(ADMIN_SEASON_PATH)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startedAt":"2026-12-01T00:00:00Z","endedAt":"2026-09-01T00:00:00Z"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_SEASON_PERIOD.name()));

        assertThat(seasonRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("시즌 전환은 관리자 토큰 없이는 호출할 수 없다")
    void changeSeason_requiresAdminToken() throws Exception {
        mockMvc.perform(post(ADMIN_SEASON_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startedAt":"2026-09-01T00:00:00Z","endedAt":"2026-12-01T00:00:00Z"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));

        assertThat(seasonRepository.findAllByIsActiveTrue()).isEmpty();
    }

    private String period(Instant startedAt, Instant endedAt) {
        return "{\"startedAt\":\"%s\",\"endedAt\":\"%s\"}".formatted(startedAt, endedAt);
    }

    private String internalReassignPath(Long seasonId) {
        return "/internal/v1/seasons/" + seasonId + "/pools/reassign";
    }

    private User eligibleUser(Gender gender, int poolNumber) {
        User user = saveUser();
        ReflectionTestUtils.setField(user, "gender", gender);
        ReflectionTestUtils.setField(user, "poolNumber", poolNumber);
        userEntitlementRepository.save(UserEntitlement.create(
                user.getId(), EntitlementReader.SIMULATION_ACCESS, Instant.now().plus(Duration.ofDays(30)), Instant.now()));
        agreeRequiredParallelEntryPolicies(user.getId());

        return user;
    }
}
