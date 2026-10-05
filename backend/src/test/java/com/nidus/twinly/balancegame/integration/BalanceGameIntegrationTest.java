package com.nidus.twinly.balancegame.integration;

import com.jayway.jsonpath.JsonPath;
import com.nidus.twinly.balancegame.domain.BalanceGameSchedule;
import com.nidus.twinly.balancegame.entity.BalanceGameAnswer;
import com.nidus.twinly.balancegame.entity.BalanceGameRound;
import com.nidus.twinly.balancegame.event.BalanceGameSummaryEvent;
import com.nidus.twinly.balancegame.repository.BalanceGameAnswerRepository;
import com.nidus.twinly.balancegame.repository.BalanceGameRoundRepository;
import com.nidus.twinly.balancegame.service.BalanceGameSummaryService;
import com.nidus.twinly.block.entity.Block;
import com.nidus.twinly.block.repository.BlockRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RecordApplicationEvents
class BalanceGameIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    RelationshipRepository relationshipRepository;

    @Autowired
    IntimacyBonusRepository intimacyBonusRepository;

    @Autowired
    BalanceGameRoundRepository balanceGameRoundRepository;

    @Autowired
    BalanceGameAnswerRepository balanceGameAnswerRepository;

    @Autowired
    BalanceGameSummaryService balanceGameSummaryService;

    @Autowired
    BlockRepository blockRepository;

    @Autowired
    ApplicationEvents applicationEvents;

    private User me;
    private User partner;
    private User other;

    @BeforeEach
    void setUp() {
        // given: 나는 상대·다른 상대와 서로 아는 사이(친밀도 40), 상대와 다른 상대는 서로 모른다
        me = saveUser();
        partner = saveUser();
        other = saveUser();
        knowEachOther(me, partner);
        knowEachOther(me, other);
    }

    @Test
    @DisplayName("이번 회차에는 누구의 프로필에서 조회하든 모두 같은 회차·같은 질문을 받는다")
    void everyone_gets_the_same_round_and_question() throws Exception {
        // when: 서로 다른 사람·다른 상대 프로필에서 조회
        String mine = body(current(me, partner).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("waitingMe")));
        String partners = body(current(partner, me).andExpect(status().isOk()));
        String others = body(current(other, me).andExpect(status().isOk()));

        // then
        assertThat(List.<String>of(JsonPath.read(partners, "$.roundId"), JsonPath.read(others, "$.roundId")))
                .containsOnly(JsonPath.<String>read(mine, "$.roundId"));
        assertThat(List.<String>of(JsonPath.read(partners, "$.question.id"), JsonPath.read(others, "$.question.id")))
                .containsOnly(JsonPath.<String>read(mine, "$.question.id"));
        assertThat(balanceGameRoundRepository.findByStartsAt(thisRound())).isPresent();
    }

    @Test
    @DisplayName("한 번 답하면 먼저 같은 답을 고른 목록의 모든 상대와 각각 2점이 쌓이고, 서로 모르는 사람끼리는 쌓이지 않는다")
    void one_answer_is_compared_with_everyone_in_my_list() throws Exception {
        // given: 상대와 다른 상대가 먼저 같은 1번을 골랐다 (둘은 서로 모르는 사이)
        String round = body(current(me, partner));
        String roundId = JsonPath.read(round, "$.roundId");
        String optionId = JsonPath.read(round, "$.question.options[0].id");
        answer(partner, roundId, optionId).andExpect(status().isOk());
        answer(other, roundId, optionId).andExpect(status().isOk());

        // when: 나도 1번
        answer(me, roundId, optionId).andExpect(status().isOk());

        // then: 나와 두 상대 각각의 쌍에만 2점, 서로 모르는 상대·다른 상대 사이에는 없다
        assertThat(bonusesBetween(me, partner)).containsExactly(2);
        assertThat(bonusesBetween(me, other)).containsExactly(2);
        assertThat(bonusesBetween(partner, other)).isEmpty();

        // then: 각 상대의 프로필에서 일치 결과와 오른 친밀도가 보인다
        current(me, partner)
                .andExpect(jsonPath("$.status").value("matched"))
                .andExpect(jsonPath("$.partnerOptionId").value(optionId))
                .andExpect(jsonPath("$.intimacyBonus").value(2));
        current(partner, me).andExpect(jsonPath("$.status").value("matched"));
        profile(me, partner).andExpect(jsonPath("$.intimacy").value(42)).andExpect(jsonPath("$.gameIntimacy").value(2));
        profile(other, me).andExpect(jsonPath("$.intimacy").value(42)).andExpect(jsonPath("$.gameIntimacy").value(2));
    }

    @Test
    @DisplayName("상대가 아직 답하지 않았으면 기다리는 상태이고, 상대가 다른 답을 고르면 불일치로 점수 없이 끝난다")
    void waits_for_partner_then_mismatches() throws Exception {
        // given: 내가 먼저 1번
        String round = body(current(me, partner));
        String roundId = JsonPath.read(round, "$.roundId");
        answer(me, roundId, JsonPath.read(round, "$.question.options[0].id")).andExpect(status().isOk());

        // then: 내 쪽은 상대를 기다리고, 상대 쪽은 내가 답했다는 것만 안다
        current(me, partner)
                .andExpect(jsonPath("$.status").value("waitingPartner"))
                .andExpect(jsonPath("$.partnerOptionId").isEmpty());
        current(partner, me)
                .andExpect(jsonPath("$.status").value("waitingMe"))
                .andExpect(jsonPath("$.partnerAnswered").value(true))
                .andExpect(jsonPath("$.partnerOptionId").isEmpty());

        // when: 상대는 2번
        answer(partner, roundId, JsonPath.read(round, "$.question.options[1].id")).andExpect(status().isOk());

        // then
        current(me, partner)
                .andExpect(jsonPath("$.status").value("mismatched"))
                .andExpect(jsonPath("$.intimacyBonus").value(0));
        assertThat(bonusesBetween(me, partner)).isEmpty();
    }

    @Test
    @DisplayName("차단한 상대와는 같은 답을 골라도 점수가 쌓이지 않는다")
    void blocked_partner_gets_no_bonus() throws Exception {
        // given: 내가 다른 상대를 차단했고, 다른 상대가 먼저 1번
        blockRepository.save(Block.create(me.getId(), other.getId()));
        String round = body(current(me, partner));
        String roundId = JsonPath.read(round, "$.roundId");
        String optionId = JsonPath.read(round, "$.question.options[0].id");
        answer(other, roundId, optionId).andExpect(status().isOk());

        // when: 나도 1번
        answer(me, roundId, optionId).andExpect(status().isOk());

        // then
        assertThat(bonusesBetween(me, other)).isEmpty();
    }

    @Test
    @DisplayName("이미 답한 회차에 다시 답하면 409, 없는 회차면 404 다")
    void rejects_second_answer_and_unknown_round() throws Exception {
        // given: 내가 이미 답했다
        String round = body(current(me, partner));
        String roundId = JsonPath.read(round, "$.roundId");
        answer(me, roundId, JsonPath.read(round, "$.question.options[0].id")).andExpect(status().isOk());

        // when & then
        answer(me, roundId, JsonPath.read(round, "$.question.options[1].id"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INTIMACY_QUIZ_ALREADY_ANSWERED"));
        answer(me, String.valueOf(Long.parseLong(roundId) + 1_000_000L), "1")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INTIMACY_QUIZ_NOT_FOUND"));
    }

    @Test
    @DisplayName("사람 목록에 없는 상대의 프로필에서는 게임을 볼 수 없다 (404 RELATIONSHIP_NOT_FOUND)")
    void stranger_has_no_game() throws Exception {
        // when & then: 상대와 다른 상대는 서로 모른다
        current(partner, other)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RELATIONSHIP_NOT_FOUND"));
    }

    @Test
    @DisplayName("지난 회차가 끝나면 같은 답을 고른 상대가 있는 유저별 인원을 한 번만 요약한다")
    void summarizes_ended_round_once() {
        // given: 지난 회차에 나·상대·다른 상대가 모두 1번
        Instant lastRound = BalanceGameSchedule.previousRoundStartOf(KstTimes.now());
        balanceGameRoundRepository.upsert(lastRound, 1L);
        BalanceGameRound round = balanceGameRoundRepository.findByStartsAt(lastRound).orElseThrow();
        balanceGameAnswerRepository.saveAll(List.of(
                BalanceGameAnswer.create(round.getId(), me.getId(), 1L),
                BalanceGameAnswer.create(round.getId(), partner.getId(), 1L),
                BalanceGameAnswer.create(round.getId(), other.getId(), 1L)));

        // when: 이번 회차 시작 10초에 도는 요약을 (서버 재시도처럼) 두 번 실행
        balanceGameSummaryService.sendEndedRound(thisRound().plusSeconds(10));
        balanceGameSummaryService.sendEndedRound(thisRound().plusSeconds(10));

        // then: 나는 2명, 서로 모르는 상대·다른 상대는 각자 나 1명과만 일치했고, 요약은 한 번만 나간다
        assertThat(applicationEvents.stream(BalanceGameSummaryEvent.class).toList())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.roundId()).isEqualTo(round.getId());
                    assertThat(event.matchedCountByUserId()).containsExactlyInAnyOrderEntriesOf(Map.of(
                            me.getId(), 2L, partner.getId(), 1L, other.getId(), 1L));
                });
    }

    private void knowEachOther(User user1, User user2) {
        LocalDate yesterday = KstTimes.today().minusDays(1);
        relationshipRepository.save(Relationship.create(user1.getId(), yesterday, "v1", user2.getId(), 40, "model",
                yesterday.atTime(23, 0), null));
        relationshipRepository.save(Relationship.create(user2.getId(), yesterday, "v1", user1.getId(), 40, "model",
                yesterday.atTime(23, 0), null));
    }

    private List<Integer> bonusesBetween(User user1, User user2) {
        return intimacyBonusRepository.findAllByUserAIdAndUserBId(
                        Math.min(user1.getId(), user2.getId()), Math.max(user1.getId(), user2.getId())).stream()
                .map(IntimacyBonus::getAmount)
                .toList();
    }

    private Instant thisRound() {
        return BalanceGameSchedule.roundStartOf(KstTimes.now());
    }

    private String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private ResultActions current(User user, User partner) throws Exception {
        return mockMvc.perform(get("/api/v1/people/{userId}/intimacy-quiz", partner.getId().toString())
                .header("Authorization", bearer(user.getId())));
    }

    private ResultActions answer(User user, String roundId, String optionId) throws Exception {
        return mockMvc.perform(post("/api/v1/intimacy-quizzes/{roundId}/answers", roundId)
                .header("Authorization", bearer(user.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"optionId\": \"" + optionId + "\"}"));
    }

    private ResultActions profile(User user, User partner) throws Exception {
        return mockMvc.perform(get("/api/v1/people/{userId}/profile", partner.getId().toString())
                .header("Authorization", bearer(user.getId())));
    }
}
