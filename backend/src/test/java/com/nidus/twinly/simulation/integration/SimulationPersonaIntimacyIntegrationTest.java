package com.nidus.twinly.simulation.integration;

import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.notification.repository.AppNotificationScheduleRepository;
import com.nidus.twinly.people.repository.EncounterRepository;
import com.nidus.twinly.purchase.entity.UserEntitlement;
import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.purchase.repository.UserEntitlementRepository;
import com.nidus.twinly.relationship.domain.Intimacy;
import com.nidus.twinly.relationship.entity.IntimacyBonus;
import com.nidus.twinly.relationship.entity.Relationship;
import com.nidus.twinly.relationship.reader.IntimacyReader;
import com.nidus.twinly.relationship.repository.IntimacyBonusRepository;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 페르소나 조회는 트랜잭션 없이(NOT_SUPPORTED) 실행되어 테스트 트랜잭션 안의 미커밋 픽스처를 보지 못한다.
 * 그래서 이 클래스만 테스트 트랜잭션을 끄고 픽스처를 실제 커밋한 뒤 직접 정리한다.
 * MySQL 컨테이너를 다른 통합 테스트와 공유하므로 정리를 빠뜨리면 그쪽까지 오염된다.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SimulationPersonaIntimacyIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate PREVIOUS_DATE = LocalDate.of(2026, 8, 17);
    private static final LocalDate DATE = LocalDate.of(2026, 8, 18);

    @Autowired
    UserEntitlementRepository userEntitlementRepository;

    @Autowired
    RelationshipRepository relationshipRepository;

    @Autowired
    IntimacyBonusRepository intimacyBonusRepository;

    @Autowired
    IntimacyReader intimacyReader;

    @Autowired
    EncounterRepository encounterRepository;

    @Autowired
    AppNotificationScheduleRepository appNotificationScheduleRepository;

    @AfterEach
    void cleanUp() {
        // 롤백이 없으므로 직접 지운다. 순서는 FK 의존의 역방향(자식 → 부모)
        appNotificationScheduleRepository.deleteAll();
        encounterRepository.deleteAll();
        intimacyBonusRepository.deleteAll();
        relationshipRepository.deleteAll();
        userEntitlementRepository.deleteAll();
        agreementRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("페르소나가 내려준 기준 시각을 결과에 그대로 돌려주면, 조회 전 게임 점수는 AI 시작값에, 조회 뒤 게임 점수는 결과 위에 더해진다")
    void intimacy_as_of_round_trips_between_persona_and_simulations() throws Exception {
        // given: 시뮬레이션 권한과 동의가 있는 나, 전날 기록 30, 페르소나 조회 1시간 전에 얻은 게임 점수 +3
        User me = saveUser();
        User partner = saveUser();
        grantSimulationAccess(me);
        relationshipRepository.save(Relationship.create(me.getId(), PREVIOUS_DATE, "v1", partner.getId(), 30, "model",
                PREVIOUS_DATE.atTime(23, 0), null));
        saveBonus(me, partner, 3, Instant.now().minus(Duration.ofHours(1)));

        // when: AI 가 다음 날짜를 시뮬레이션하려고 페르소나 조회
        String persona = mockMvc.perform(get("/internal/v1/users/{userId}/persona", me.getId().toString())
                        .param("date", DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intimacies[0].partnerId").value(partner.getId().toString()))
                .andExpect(jsonPath("$.intimacies[0].intimacy").value(33))
                .andReturn().getResponse().getContentAsString();
        String intimacyAsOf = JsonPath.read(persona, "$.intimacyAsOf");

        // then: 기준 시각은 초 단위 KST 벽시계 형식이다
        assertThat(intimacyAsOf).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");

        // given: 페르소나 조회 뒤, AI 가 시뮬레이션하는 동안 얻은 게임 점수 +4
        saveBonus(me, partner, 4, Instant.now());

        // when: AI 가 33 에서 출발해 35 를 낸 결과를 받은 기준 시각 그대로 저장
        mockMvc.perform(post("/internal/v1/users/{userId}/simulations", me.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(simulationsPayload(me, partner, 35, intimacyAsOf)))
                .andExpect(status().isOk());

        // then: 기준 시각이 내려준 값과 같은 순간으로 저장된다
        assertThat(relationshipRepository.findAll().stream()
                .filter(relationship -> relationship.getUserId().equals(me.getId()) && relationship.getDate().equals(DATE))
                .map(Relationship::getIntimacyAsOf)
                .toList())
                .containsExactly(KstTimes.toInstant(LocalDateTime.parse(intimacyAsOf)));

        // then: +3 은 AI 시작값에 이미 들어 있으므로 빼고 35 + 4, 게임 몫은 3 + 4
        assertThat(intimacyReader.read(me.getId(), partner.getId(), KstTimes.now())).isEqualTo(new Intimacy(39, 7));
    }

    private void grantSimulationAccess(User user) {
        userEntitlementRepository.save(UserEntitlement.create(user.getId(), EntitlementReader.SIMULATION_ACCESS,
                Instant.now().plus(Duration.ofDays(1)), Instant.now()));
        agreeRequiredParallelEntryPolicies(user.getId());
    }

    private void saveBonus(User user1, User user2, int amount, Instant createdAt) {
        IntimacyBonus bonus = IntimacyBonus.create(user1.getId(), user2.getId(), amount);
        ReflectionTestUtils.setField(bonus, "createdAt", createdAt);
        intimacyBonusRepository.save(bonus);
    }

    private String simulationsPayload(User me, User partner, int rapport, String intimacyAsOf) {
        return """
                {
                  "userId": "%d",
                  "date": "%s",
                  "scenes": [],
                  "questions": [],
                  "relationships": [
                    {
                      "partnerId": "%d",
                      "updateTime": "%sT22:00:00",
                      "rapport": %d,
                      "partnerModel": "model-v2"
                    }
                  ],
                  "intimacyAsOf": "%s"
                }
                """.formatted(me.getId(), DATE, partner.getId(), DATE, rapport, intimacyAsOf);
    }
}
