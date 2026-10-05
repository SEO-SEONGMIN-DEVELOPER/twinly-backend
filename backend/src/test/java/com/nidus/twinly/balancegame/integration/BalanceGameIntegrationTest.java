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
        // given: 나는 트윈·다른 트윈과 서로 아는 사이(친밀도 40), 트윈과 다른 트윈은 서로 모른다
        me = saveUser();
        partner = saveUser();
        other = saveUser();
        knowEachOther(me, partner);
        knowEachOther(me, other);
    }

    @Test
    @DisplayName("이번 회차에는 누구의 프로필에서 조회하든 모두 같은 회차·같은 질문을 받는다")
    void everyone_gets_the_same_round_and_question() throws Exception {
        // when: 서로 다른 사람·다른 트윈 프로필에서 조회
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
    @DisplayName("같은 질문이라도 트윈마다 따로 답하고, 서로에게 같은 답을 한 쌍에만 2점이 쌓인다")
    void answers_are_separate_per_partner() throws Exception {
        // given: 이번 회차
        String round = body(current(me, partner));
        String roundId = JsonPath.read(round, "$.roundId");
        String first = JsonPath.read(round, "$.question.options[0].id");
        String second = JsonPath.read(round, "$.question.options[1].id");

        // when: 나는 트윈에게 1번, 다른 트윈에게 2번. 두 트윈은 모두 나에게 1번
        answer(me, roundId, partner, first).andExpect(status().isOk());
        answer(me, roundId, other, second).andExpect(status().isOk());
        answer(partner, roundId, me, first).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("matched"))
                .andExpect(jsonPath("$.intimacyBonus").value(2));
        answer(other, roundId, me, first).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("mismatched"))
                .andExpect(jsonPath("$.partnerOptionId").value(second));

        // then: 트윈과는 일치해 2점, 다른 트윈과는 불일치라 점수 없음
        assertThat(bonusesBetween(me, partner)).containsExactly(2);
        assertThat(bonusesBetween(me, other)).isEmpty();
        current(me, partner).andExpect(jsonPath("$.status").value("matched"));
        current(me, other).andExpect(jsonPath("$.status").value("mismatched"));
        profile(me, partner).andExpect(jsonPath("$.intimacy").value(42)).andExpect(jsonPath("$.gameIntimacy").value(2));
        profile(me, other).andExpect(jsonPath("$.intimacy").value(40)).andExpect(jsonPath("$.gameIntimacy").value(0));
    }

    @Test
    @DisplayName("한 트윈에게 한 답은 다른 트윈과의 비교에 쓰이지 않는다")
    void answer_to_one_partner_is_not_used_for_another() throws Exception {
        // given: 나는 트윈에게만 1번을 답했다
        String round = body(current(me, partner));
        String roundId = JsonPath.read(round, "$.roundId");
        String first = JsonPath.read(round, "$.question.options[0].id");
        answer(me, roundId, partner, first).andExpect(status().isOk());

        // when: 다른 트윈이 나에게 같은 1번
        answer(other, roundId, me, first).andExpect(status().isOk());

        // then: 내가 다른 트윈에게는 아직 답하지 않았으므로 기다리는 상태이고 점수도 없다
        current(other, me).andExpect(jsonPath("$.status").value("waitingPartner"));
        current(me, other)
                .andExpect(jsonPath("$.status").value("waitingMe"))
                .andExpect(jsonPath("$.partnerAnswered").value(true))
                .andExpect(jsonPath("$.partnerOptionId").isEmpty());
        assertThat(bonusesBetween(me, other)).isEmpty();
    }

    @Test
    @DisplayName("차단한 상대에게는 답할 수 없다 (404 RELATIONSHIP_NOT_FOUND)")
    void cannot_answer_to_blocked_partner() throws Exception {
        // given: 내가 다른 트윈을 차단했다
        String round = body(current(me, partner));
        String roundId = JsonPath.read(round, "$.roundId");
        blockRepository.save(Block.create(me.getId(), other.getId()));

        // when & then
        answer(me, roundId, other, JsonPath.read(round, "$.question.options[0].id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RELATIONSHIP_NOT_FOUND"));
    }

    @Test
    @DisplayName("같은 트윈에게 다시 답하면 409 지만, 다른 트윈에게는 답할 수 있다. 없는 회차면 404 다")
    void rejects_second_answer_to_same_partner_only() throws Exception {
        // given: 트윈에게 이미 답했다
        String round = body(current(me, partner));
        String roundId = JsonPath.read(round, "$.roundId");
        String first = JsonPath.read(round, "$.question.options[0].id");
        answer(me, roundId, partner, first).andExpect(status().isOk());

        // when & then
        answer(me, roundId, partner, JsonPath.read(round, "$.question.options[1].id"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INTIMACY_QUIZ_ALREADY_ANSWERED"));
        answer(me, roundId, other, first).andExpect(status().isOk());
        answer(me, String.valueOf(Long.parseLong(roundId) + 1_000_000L), partner, "1")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INTIMACY_QUIZ_NOT_FOUND"));
    }

    @Test
    @DisplayName("사람 목록에 없는 상대의 프로필에서는 게임을 볼 수 없다 (404 RELATIONSHIP_NOT_FOUND)")
    void stranger_has_no_game() throws Exception {
        // when & then: 트윈과 다른 트윈은 서로 모른다
        current(partner, other)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RELATIONSHIP_NOT_FOUND"));
    }

    @Test
    @DisplayName("지난 회차가 끝나면 서로에게 같은 답을 한 트윈 수를 유저별로 한 번만 요약한다")
    void summarizes_ended_round_once() {
        // given: 지난 회차에 나와 트윈은 서로 1번(일치), 나는 다른 트윈에게 1번·다른 트윈은 나에게 2번(불일치)
        Instant lastRound = BalanceGameSchedule.previousRoundStartOf(KstTimes.now());
        balanceGameRoundRepository.upsert(lastRound, 1L);
        BalanceGameRound round = balanceGameRoundRepository.findByStartsAt(lastRound).orElseThrow();
        balanceGameAnswerRepository.saveAll(List.of(
                BalanceGameAnswer.create(round.getId(), me.getId(), partner.getId(), 1L),
                BalanceGameAnswer.create(round.getId(), partner.getId(), me.getId(), 1L),
                BalanceGameAnswer.create(round.getId(), me.getId(), other.getId(), 1L),
                BalanceGameAnswer.create(round.getId(), other.getId(), me.getId(), 2L)));

        // when: 이번 회차 시작 10초에 도는 요약을 (서버 재시도처럼) 두 번 실행
        balanceGameSummaryService.sendEndedRound(thisRound().plusSeconds(10));
        balanceGameSummaryService.sendEndedRound(thisRound().plusSeconds(10));

        // then: 나와 트윈만 1명씩, 다른 트윈은 일치한 상대가 없어 빠지고, 요약은 한 번만 나간다
        assertThat(applicationEvents.stream(BalanceGameSummaryEvent.class).toList())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.roundId()).isEqualTo(round.getId());
                    assertThat(event.matchedCountByUserId()).containsExactlyInAnyOrderEntriesOf(Map.of(
                            me.getId(), 1L, partner.getId(), 1L));
                });
    }

    @Test
    @DisplayName("역대 일치도는 서로에게 답한 회차만 세고, 한쪽만 답한 회차와 다른 트윈과의 답은 섞지 않는다")
    void match_rate_counts_only_rounds_answered_by_both() throws Exception {
        // given: 지난 세 회차 — 1회차 서로 1번(일치), 2회차 나 1번·트윈 2번(불일치), 3회차 나만 답함. 다른 트윈과는 1회차에 서로 1번
        BalanceGameRound first = pastRound("2026-09-01T03:00:00Z");
        BalanceGameRound second = pastRound("2026-09-01T09:00:00Z");
        BalanceGameRound third = pastRound("2026-09-02T03:00:00Z");
        balanceGameAnswerRepository.saveAll(List.of(
                BalanceGameAnswer.create(first.getId(), me.getId(), partner.getId(), 1L),
                BalanceGameAnswer.create(first.getId(), partner.getId(), me.getId(), 1L),
                BalanceGameAnswer.create(second.getId(), me.getId(), partner.getId(), 1L),
                BalanceGameAnswer.create(second.getId(), partner.getId(), me.getId(), 2L),
                BalanceGameAnswer.create(third.getId(), me.getId(), partner.getId(), 1L),
                BalanceGameAnswer.create(first.getId(), me.getId(), other.getId(), 1L),
                BalanceGameAnswer.create(first.getId(), other.getId(), me.getId(), 1L)));

        // when & then: 비교 2회 중 일치 1회
        matchRate(me, partner).andExpect(status().isOk())
                .andExpect(jsonPath("$.comparedCount").value(2))
                .andExpect(jsonPath("$.matchedCount").value(1))
                .andExpect(jsonPath("$.matchRate").value(50.0));

        // then: 트윈 쪽에서 봐도 같다
        matchRate(partner, me)
                .andExpect(jsonPath("$.comparedCount").value(2))
                .andExpect(jsonPath("$.matchRate").value(50.0));
    }

    @Test
    @DisplayName("아직 서로 답한 회차가 없으면 일치도는 null 이다")
    void match_rate_is_null_without_comparison() throws Exception {
        // when & then
        matchRate(me, partner).andExpect(status().isOk())
                .andExpect(jsonPath("$.comparedCount").value(0))
                .andExpect(jsonPath("$.matchedCount").value(0))
                .andExpect(jsonPath("$.matchRate").isEmpty());
    }

    private BalanceGameRound pastRound(String startsAt) {
        balanceGameRoundRepository.upsert(Instant.parse(startsAt), 1L);
        return balanceGameRoundRepository.findByStartsAt(Instant.parse(startsAt)).orElseThrow();
    }

    private ResultActions matchRate(User user, User partner) throws Exception {
        return mockMvc.perform(get("/api/v1/people/{userId}/intimacy-quiz/match-rate", partner.getId().toString())
                .header("Authorization", bearer(user.getId())));
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

    private ResultActions answer(User user, String roundId, User partner, String optionId) throws Exception {
        return mockMvc.perform(post("/api/v1/intimacy-quizzes/{roundId}/answers", roundId)
                .header("Authorization", bearer(user.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"partnerId\": \"" + partner.getId() + "\", \"optionId\": \"" + optionId + "\"}"));
    }

    private ResultActions profile(User user, User partner) throws Exception {
        return mockMvc.perform(get("/api/v1/people/{userId}/profile", partner.getId().toString())
                .header("Authorization", bearer(user.getId())));
    }
}
