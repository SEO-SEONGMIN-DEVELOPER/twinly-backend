package com.nidus.twinly.season.integration;

import com.nidus.twinly.season.reader.CurrentSeasonReader;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.me.service.MeService;
import com.nidus.twinly.notification.domain.AppNotificationScheduleType;
import com.nidus.twinly.notification.entity.AppNotificationSchedule;
import com.nidus.twinly.notification.repository.AppNotificationScheduleRepository;
import com.nidus.twinly.people.repository.EncounterRepository;
import com.nidus.twinly.purchase.client.RevenueCatClient;
import com.nidus.twinly.relationship.entity.Relationship;
import com.nidus.twinly.relationship.reader.IntimacyReader;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.purchase.client.RevenueCatEntitlement;
import com.nidus.twinly.purchase.entity.UserEntitlement;
import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.purchase.repository.UserEntitlementRepository;
import com.nidus.twinly.season.entity.SeasonParticipation;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeasonIntegrationTest extends AbstractIntegrationTest {

    /** 활성 시즌으로 넣을 시즌 id. */
    private static final long CURRENT_SEASON_ID = 1L;

    @Autowired
    CurrentSeasonReader currentSeasonReader;

    @Autowired
    SeasonParticipationRepository seasonParticipationRepository;

    @Autowired
    UserEntitlementRepository userEntitlementRepository;

    @Autowired
    RelationshipRepository relationshipRepository;

    @Autowired
    EncounterRepository encounterRepository;

    @Autowired
    IntimacyReader intimacyReader;

    @Autowired
    MeService meService;

    @Autowired
    AppNotificationScheduleRepository appNotificationScheduleRepository;

    // 참여 API 가 RevenueCat 과 먼저 동기화하므로 실제 호출을 막는다.
    @MockitoBean
    RevenueCatClient revenueCatClient;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    @DisplayName("시즌 참가 조회 성공: 참가 이력이 있으면 현재 시즌 id와 참가 시각을 응답한다")
    void participation_when_participated_end_to_end() throws Exception {
        // given: 현재 시즌과 유저를 저장하고 고정된 참가 시각으로 참가 이력을 DB에 만든다
        Instant now = Instant.now();
        saveCurrentSeason(now.minus(Duration.ofDays(30)), now.plus(Duration.ofDays(30)));
        User user = saveUser();
        SeasonParticipation participation = SeasonParticipation.create(user.getId(), CURRENT_SEASON_ID);
        ReflectionTestUtils.setField(participation, "participatedInAt", Instant.parse("2026-07-01T00:00:00Z"));
        seasonParticipationRepository.save(participation);

        // when: 해당 유저의 실제 액세스 토큰으로 시즌 참가 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/season/participation")
                .header("Authorization", bearer(user.getId())));

        // then: 현재 시즌 id는 문자열로, 참가 시각은 KstInstantSerializer가 KST 오프셋으로 직렬화하여 응답
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.currentSeasonId").value(String.valueOf(CURRENT_SEASON_ID)))
                .andExpect(jsonPath("$.participatedInAt").value("2026-07-01T09:00:00+09:00"));
    }

    @Test
    @DisplayName("시즌 참가 조회 성공: 참가 이력이 없으면 participatedInAt이 null로 응답된다")
    void participation_when_not_participated_end_to_end() throws Exception {
        // given: 활성 시즌만 있고 참가 이력이 없는 실제 유저
        Instant now = Instant.now();
        saveCurrentSeason(now.minus(Duration.ofDays(30)), now.plus(Duration.ofDays(30)));
        User user = saveUser();

        // when: 해당 유저의 실제 액세스 토큰으로 시즌 참가 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/season/participation")
                .header("Authorization", bearer(user.getId())));

        // then: 현재 시즌 id는 채워지고 참가 시각은 null
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.currentSeasonId").value(String.valueOf(CURRENT_SEASON_ID)))
                .andExpect(jsonPath("$.participatedInAt", nullValue()));
    }

    @Test
    @DisplayName("시즌 참여 성공: 결제·필수 약관 동의를 갖춘 유저에게 현재 시즌 참가 행이 생긴다")
    void participateIn_end_to_end() throws Exception {
        // given: 진행 중인 시즌과 결제·필수 약관 동의를 모두 갖춘 유저
        Instant now = Instant.now();
        saveCurrentSeason(now.minus(Duration.ofDays(30)), now.plus(Duration.ofDays(30)));
        User user = saveEntitledUser(now);
        agreeRequiredParallelEntryPolicies(user.getId());

        // when: 시즌 참여 API 호출
        mockMvc.perform(put("/api/v1/season/participation")
                        .header("Authorization", bearer(user.getId())))
                .andExpect(status().isOk());

        // then: 현재 시즌 참가 행이 생긴다
        assertThat(seasonParticipationRepository.findByUserIdAndSeasonId(user.getId(), CURRENT_SEASON_ID)).isPresent();
    }

    @Test
    @DisplayName("시즌 첫 참여: 같은 풀 이성 2명과 0점 인연이 생기되 인연탭엔 1명만 바로, 나머지는 다음 날 보이고 페르소나엔 둘 다 실린다")
    void participateIn_connects_starter_relationships_end_to_end() throws Exception {
        // given: 풀 13의 남자 신규 유저, 같은 풀에 참가 중인 여자 2명·남자 1명, 다른 풀의 여자 1명
        Instant now = Instant.now();
        saveCurrentSeason(now.minus(Duration.ofDays(30)), now.plus(Duration.ofDays(30)));
        User user = saveEntitledUser(now);
        agreeRequiredParallelEntryPolicies(user.getId());
        placeInPool(user, Gender.MALE, 13);
        User femaleA = saveParticipant(Gender.FEMALE, 13);
        User femaleB = saveParticipant(Gender.FEMALE, 13);
        User male = saveParticipant(Gender.MALE, 13);
        User otherPoolFemale = saveParticipant(Gender.FEMALE, 14);

        // when: 시즌 참여 API 호출
        mockMvc.perform(put("/api/v1/season/participation")
                        .header("Authorization", bearer(user.getId())))
                .andExpect(status().isOk());

        // then: 지금은 같은 풀 여자 중 1명만 양쪽 방향으로 보인다
        entityManager.clear();
        LocalDateTime kstNow = KstTimes.now();
        List<Long> candidateUserIds = List.of(femaleA.getId(), femaleB.getId(), male.getId(), otherPoolFemale.getId());
        List<Relationship> revealed = relationshipRepository.findLatestUntilByUserIdAndPartnerUserIdIn(user.getId(), candidateUserIds, kstNow);
        assertThat(revealed).hasSize(1);
        assertThat(revealed.getFirst().getIntimacy()).isZero();
        Long firstPartnerUserId = revealed.getFirst().getPartnerUserId();
        assertThat(firstPartnerUserId).isIn(femaleA.getId(), femaleB.getId());
        Long secondPartnerUserId = firstPartnerUserId.equals(femaleA.getId()) ? femaleB.getId() : femaleA.getId();
        assertThat(relationshipRepository.findLatestUntilByUserIdAndPartnerUserId(firstPartnerUserId, user.getId(), kstNow)).isPresent();
        assertThat(relationshipRepository.findLatestUntilByUserIdAndPartnerUserId(secondPartnerUserId, user.getId(), kstNow)).isEmpty();

        // then: 하루가 지나면 나머지 1명도 양쪽 방향으로 보인다
        LocalDateTime nextDay = kstNow.plusDays(1);
        assertThat(relationshipRepository.findLatestUntilByUserIdAndPartnerUserIdIn(user.getId(), candidateUserIds, nextDay))
                .extracting(Relationship::getPartnerUserId, Relationship::getIntimacy)
                .containsExactlyInAnyOrder(tuple(femaleA.getId(), 0), tuple(femaleB.getId(), 0));
        assertThat(relationshipRepository.findLatestUntilByUserIdAndPartnerUserId(secondPartnerUserId, user.getId(), nextDay)).isPresent();
        assertThat(encounterRepository.findByUserAIdAndUserBId(
                Math.min(user.getId(), secondPartnerUserId), Math.max(user.getId(), secondPartnerUserId))).isPresent();

        // then: 기존 유저 인연탭에 먼저 공개된 쪽만 신규 유저가 친밀도 0으로 보인다
        mockMvc.perform(get("/api/v1/people")
                        .header("Authorization", bearer(firstPartnerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.people[0].userId").value(String.valueOf(user.getId())))
                .andExpect(jsonPath("$.people[0].intimacy").value(0));
        mockMvc.perform(get("/api/v1/people")
                        .header("Authorization", bearer(secondPartnerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.people").isEmpty());

        // then: 신규 유저의 프로필 만난 사람 수도 공개된 1명만 센다
        assertThat(meService.profile(user.getId()).encounteredPeopleCount()).isEqualTo(1);

        // then: 양쪽 푸시 예약이 공개 시각대로 들어가, 지금은 먼저 공개된 쪽만 발송 대상이고 하루 뒤 나머지도 대상이 된다
        Instant afterParticipation = Instant.now();
        assertThat(appNotificationScheduleRepository.findAllBySentAtIsNullAndScheduledAtLessThanEqualOrderByScheduledAtAsc(afterParticipation))
                .extracting(AppNotificationSchedule::getUserId, AppNotificationSchedule::getPartnerUserId, AppNotificationSchedule::getType)
                .containsExactlyInAnyOrder(
                        tuple(user.getId(), firstPartnerUserId, AppNotificationScheduleType.STARTER_REVEAL),
                        tuple(firstPartnerUserId, user.getId(), AppNotificationScheduleType.STARTER_REVEAL));
        assertThat(appNotificationScheduleRepository.findAllBySentAtIsNullAndScheduledAtLessThanEqualOrderByScheduledAtAsc(afterParticipation.plus(Duration.ofDays(1))))
                .extracting(AppNotificationSchedule::getUserId, AppNotificationSchedule::getPartnerUserId)
                .containsExactlyInAnyOrder(
                        tuple(user.getId(), firstPartnerUserId), tuple(firstPartnerUserId, user.getId()),
                        tuple(user.getId(), secondPartnerUserId), tuple(secondPartnerUserId, user.getId()));

        // then: 선생성 첫날 페르소나에도 두 상대가 친밀도 0으로 실린다
        assertThat(intimacyReader.readForSimulation(user.getId(), KstTimes.today(), currentSeasonReader.scopeOf(KstTimes.today()), Instant.now()))
                .containsOnly(entry(femaleA.getId(), 0), entry(femaleB.getId(), 0));
    }

    @Test
    @DisplayName("시즌 참여 재호출: 이미 참여한 유저가 다시 호출해도 최초 참여 시각이 바뀌지 않는다")
    void participateIn_when_already_participated_keeps_first_participatedInAt() throws Exception {
        // given: 이미 참여해 참가 시각이 기록된 유저
        Instant now = Instant.now();
        saveCurrentSeason(now.minus(Duration.ofDays(30)), now.plus(Duration.ofDays(30)));
        User user = saveEntitledUser(now);
        agreeRequiredParallelEntryPolicies(user.getId());
        mockMvc.perform(put("/api/v1/season/participation")
                        .header("Authorization", bearer(user.getId())))
                .andExpect(status().isOk());
        Instant firstParticipatedInAt = seasonParticipationRepository
                .findByUserIdAndSeasonId(user.getId(), CURRENT_SEASON_ID).orElseThrow().getParticipatedInAt();

        // when: 같은 유저가 다시 참여 호출
        mockMvc.perform(put("/api/v1/season/participation")
                        .header("Authorization", bearer(user.getId())))
                .andExpect(status().isOk());

        // then: upsert 라 행이 새로 생기지 않고 최초 참여 시각이 유지된다
        // (영속성 컨텍스트 캐시를 비워야 덮어쓰기가 있었는지 실제 DB 상태로 확인된다)
        entityManager.clear();
        assertThat(seasonParticipationRepository.findByUserIdAndSeasonId(user.getId(), CURRENT_SEASON_ID).orElseThrow()
                .getParticipatedInAt()).isEqualTo(firstParticipatedInAt);
    }

    @Test
    @DisplayName("시즌 참여 실패: 결제 권한이 없으면 403 SIMULATION_ACCESS_REQUIRED 이고 참가 행이 생기지 않는다")
    void participateIn_without_access_end_to_end() throws Exception {
        // given: 약관에는 동의했지만 결제 권한이 없는 유저 (동기화해도 권한이 붙지 않음)
        Instant now = Instant.now();
        saveCurrentSeason(now.minus(Duration.ofDays(30)), now.plus(Duration.ofDays(30)));
        User user = saveUser();
        agreeRequiredParallelEntryPolicies(user.getId());
        given(revenueCatClient.entitlements(anyString())).willReturn(List.of());

        // when & then: 권한 부족으로 거절
        mockMvc.perform(put("/api/v1/season/participation")
                        .header("Authorization", bearer(user.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SIMULATION_ACCESS_REQUIRED"));

        assertThat(seasonParticipationRepository.findByUserIdAndSeasonId(user.getId(), CURRENT_SEASON_ID)).isEmpty();
    }

    @Test
    @DisplayName("시즌 참여 실패: 필수 약관에 동의하지 않았으면 403 SIMULATION_CONSENT_REQUIRED 이고 참가 행이 생기지 않는다")
    void participateIn_without_consent_end_to_end() throws Exception {
        // given: 결제는 했지만 평행우주 입장 필수 약관에 동의하지 않은 유저
        Instant now = Instant.now();
        saveCurrentSeason(now.minus(Duration.ofDays(30)), now.plus(Duration.ofDays(30)));
        User user = saveEntitledUser(now);

        // when & then: 약관 미동의로 거절
        mockMvc.perform(put("/api/v1/season/participation")
                        .header("Authorization", bearer(user.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SIMULATION_CONSENT_REQUIRED"));

        assertThat(seasonParticipationRepository.findByUserIdAndSeasonId(user.getId(), CURRENT_SEASON_ID)).isEmpty();
    }

    /**
     * 결제한 유저. 동기화가 저장된 권한을 지우지 않도록 RevenueCat 응답도 같은 권한으로 맞춘다.
     * (replaceEntitlements 는 응답에 없는 권한을 삭제한다)
     */
    private User saveEntitledUser(Instant now) {
        User user = saveUser();
        Instant expiresAt = now.plus(Duration.ofDays(30));
        userEntitlementRepository.save(UserEntitlement.create(
                user.getId(), EntitlementReader.SIMULATION_ACCESS, expiresAt, now));
        given(revenueCatClient.entitlements(user.getRevenueCatUserId().toString()))
                .willReturn(List.of(new RevenueCatEntitlement(EntitlementReader.SIMULATION_ACCESS, expiresAt)));
        return user;
    }

    /** 영속 상태인 유저의 성별·풀을 바꾸고 바로 DB에 반영한다. */
    private void placeInPool(User user, Gender gender, int poolNumber) {
        ReflectionTestUtils.setField(user, "gender", gender);
        ReflectionTestUtils.setField(user, "poolNumber", poolNumber);
        userRepository.saveAndFlush(user);
    }

    /** 지정한 풀에서 현재 시즌에 참가 중인 유저. */
    private User saveParticipant(Gender gender, int poolNumber) {
        User user = saveUser();
        placeInPool(user, gender, poolNumber);
        seasonParticipationRepository.save(SeasonParticipation.create(user.getId(), CURRENT_SEASON_ID));
        return user;
    }

    /**
     * seasons는 마이그레이션 시드가 없고 엔티티에 생성 팩토리도 없으므로,
     * id와 is_active를 명시해 활성 시즌을 직접 insert 한다. (테스트 트랜잭션 롤백으로 정리됨)
     */
    private void saveCurrentSeason(Instant startedAt, Instant endedAt) {
        entityManager.createNativeQuery(
                        "INSERT INTO seasons (id, started_at, ended_at, is_active) VALUES (?1, ?2, ?3, 1)")
                .setParameter(1, CURRENT_SEASON_ID)
                .setParameter(2, Timestamp.from(startedAt))
                .setParameter(3, Timestamp.from(endedAt))
                .executeUpdate();
    }
}
