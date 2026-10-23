package com.nidus.twinly.balancegame.integration;

import com.jayway.jsonpath.JsonPath;
import com.nidus.twinly.balancegame.domain.BalanceGameSchedule;
import com.nidus.twinly.balancegame.entity.BalanceGameAnswer;
import com.nidus.twinly.balancegame.entity.BalanceGameRound;
import com.nidus.twinly.balancegame.repository.BalanceGameAnswerRepository;
import com.nidus.twinly.balancegame.repository.BalanceGameRoundRepository;
import com.nidus.twinly.block.entity.Block;
import com.nidus.twinly.block.repository.BlockRepository;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.notification.event.AppNotificationFeedCreatedEvent;
import com.nidus.twinly.relationship.entity.IntimacyBonus;
import com.nidus.twinly.relationship.entity.Relationship;
import com.nidus.twinly.relationship.repository.IntimacyBonusRepository;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.repository.SeasonRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

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
    BlockRepository blockRepository;

    @Autowired
    SeasonRepository seasonRepository;

    @Autowired
    ApplicationEvents applicationEvents;

    private Season season;
    private User me;
    private User partner;
    private User other;

    @BeforeEach
    void setUp() {
        // given: 진행 중인 시즌, 나는 트윈·다른 트윈과 서로 아는 사이(친밀도 40), 트윈과 다른 트윈은 서로 모른다
        season = seasonRepository.save(Season.create(Instant.now().minus(30, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS)));
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
        assertThat(balanceGameRoundRepository.findByStartsAt(thisRound()))
                .hasValueSatisfying(round -> assertThat(round.getSeasonId()).isEqualTo(season.getId()));
    }

    @Test
    @DisplayName("같은 질문이라도 트윈마다 따로 답하고, 서로에게 같은 답을 한 쌍에만 10점이 쌓인다")
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
                .andExpect(jsonPath("$.intimacyBonus").value(10));
        answer(other, roundId, me, first).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("mismatched"))
                .andExpect(jsonPath("$.partnerOptionId").value(second));

        // then: 트윈과는 일치해 10점, 다른 트윈과는 불일치라 점수 없음
        assertThat(bonusesBetween(me, partner)).containsExactly(10);
        assertThat(bonusesBetween(me, other)).isEmpty();
        current(me, partner).andExpect(jsonPath("$.status").value("matched"));
        current(me, other).andExpect(jsonPath("$.status").value("mismatched"));
        profile(me, partner).andExpect(jsonPath("$.intimacy").value(50)).andExpect(jsonPath("$.gameIntimacy").value(10));
        profile(me, other).andExpect(jsonPath("$.intimacy").value(40)).andExpect(jsonPath("$.gameIntimacy").value(0));
    }

    @Test
    @DisplayName("트윈에게 답하면 트윈의 알림함에 나와의 밸런스 게임을 가리키는 응답 피드가 쌓이고, 푸시 이벤트가 발행된다")
    void answer_writes_feed_to_partner() throws Exception {
        // given: 이번 회차
        String round = body(current(me, partner));
        String roundId = JsonPath.read(round, "$.roundId");
        String first = JsonPath.read(round, "$.question.options[0].id");

        // when: 내가 먼저 답하고, 트윈이 이어서 답함
        answer(me, roundId, partner, first).andExpect(status().isOk());
        answer(partner, roundId, me, first).andExpect(status().isOk());

        // then: 트윈은 아직 안 답했을 때 받은 알림 1건
        feeds(partner)
                .andExpect(jsonPath("$.appNotificationFeeds.length()").value(1))
                .andExpect(jsonPath("$.appNotificationFeeds[0].type").value("balanceGameAnswered"))
                .andExpect(jsonPath("$.appNotificationFeeds[0].title").value(me.displayNickname() + "님이 밸런스 게임에 답했어요."))
                .andExpect(jsonPath("$.appNotificationFeeds[0].body").value("나도 답하고 서로 같은 답을 골랐는지 확인해 보세요."))
                .andExpect(jsonPath("$.appNotificationFeeds[0].target.kind").value("balanceGame"))
                .andExpect(jsonPath("$.appNotificationFeeds[0].target.userId").value(me.getId().toString()));

        // then: 나는 이미 답한 뒤 받은 결과 확인 알림 1건
        feeds(me)
                .andExpect(jsonPath("$.appNotificationFeeds.length()").value(1))
                .andExpect(jsonPath("$.appNotificationFeeds[0].body").value("서로 같은 답을 골랐는지 확인해 보세요."))
                .andExpect(jsonPath("$.appNotificationFeeds[0].target.kind").value("balanceGame"))
                .andExpect(jsonPath("$.appNotificationFeeds[0].target.userId").value(partner.getId().toString()));

        assertThat(applicationEvents.stream(AppNotificationFeedCreatedEvent.class)).hasSize(2);
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
    @DisplayName("일치도는 서로에게 답한 회차만 세고, 한쪽만 답한 회차와 다른 트윈과의 답은 섞지 않는다")
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

    @Test
    @DisplayName("일치도와 기록은 이번 시즌 회차만 세고, 지난 시즌에 서로 답한 회차는 넣지 않는다")
    void match_rate_and_history_count_only_current_season() throws Exception {
        // given: 지난 시즌 회차에 서로 1번(일치), 이번 시즌 회차에 나 1번·트윈 2번(불일치)
        Season previousSeason = Season.create(Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-31T00:00:00Z"));
        previousSeason.deactivate();
        seasonRepository.save(previousSeason);
        BalanceGameRound previousSeasonRound = pastRound("2026-08-10T03:00:00Z", previousSeason);
        BalanceGameRound currentSeasonRound = pastRound("2026-09-01T03:00:00Z");
        bothAnswer(previousSeasonRound, 1L, 1L);
        bothAnswer(currentSeasonRound, 1L, 2L);

        // when & then: 일치도는 이번 시즌 1회 비교·0회 일치, 기록도 이번 시즌 회차 하나만 담는다
        matchRate(me, partner)
                .andExpect(jsonPath("$.comparedCount").value(1))
                .andExpect(jsonPath("$.matchedCount").value(0));
        history(me, partner, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.questions.length()").value(1))
                .andExpect(jsonPath("$.questions[0].roundId").value(currentSeasonRound.getId().toString()));
    }

    @Test
    @DisplayName("기록은 일치도와 같은 회차를 회차 시작 시각 최신순으로 담고, 내 답과 일치 여부를 준다")
    void history_matches_match_rate_and_orders_by_round_start() throws Exception {
        // given: 늦은 회차를 먼저 만들어 id 순서와 시작 시각 순서를 뒤집는다
        //        늦은 회차는 나 1번·트윈 2번(불일치), 이른 회차는 서로 1번(일치), 나만 답한 회차와 다른 트윈과의 답도 있다
        BalanceGameRound later = pastRound("2026-09-01T09:00:00Z");
        BalanceGameRound earlier = pastRound("2026-09-01T03:00:00Z");
        BalanceGameRound onlyMine = pastRound("2026-09-02T03:00:00Z");
        bothAnswer(later, 1L, 2L);
        bothAnswer(earlier, 1L, 1L);
        balanceGameAnswerRepository.saveAll(List.of(
                BalanceGameAnswer.create(onlyMine.getId(), me.getId(), partner.getId(), 1L),
                BalanceGameAnswer.create(earlier.getId(), me.getId(), other.getId(), 2L),
                BalanceGameAnswer.create(earlier.getId(), other.getId(), me.getId(), 2L)));

        // when: 나의 실제 토큰으로 트윈과의 기록 첫 페이지 조회
        ResultActions result = history(me, partner, null);

        // then: 일치도의 2번 중 1번과 같은 회차가 시작 시각 순서로 나온다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.questions.length()").value(2))
                .andExpect(jsonPath("$.questions[0].roundId").value(later.getId().toString()))
                .andExpect(jsonPath("$.questions[0].question.id").value("1"))
                .andExpect(jsonPath("$.questions[0].question.options.length()").value(2))
                .andExpect(jsonPath("$.questions[0].myOptionId").value("1"))
                .andExpect(jsonPath("$.questions[0].matched").value(false))
                .andExpect(jsonPath("$.questions[1].roundId").value(earlier.getId().toString()))
                .andExpect(jsonPath("$.questions[1].matched").value(true))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
        matchRate(me, partner)
                .andExpect(jsonPath("$.comparedCount").value(2))
                .andExpect(jsonPath("$.matchedCount").value(1));
    }

    @Test
    @DisplayName("기록은 20개씩 끊어 주고, 보는 도중 새 회차가 생겨도 다음 페이지에 겹치거나 빠지는 회차가 없다")
    void history_pages_by_last_round_without_overlap() throws Exception {
        // given: 서로 답한 지난 회차 21개
        List<String> roundIds = new ArrayList<>();
        IntStream.range(0, 21).forEach(day -> {
            BalanceGameRound round = pastRound(Instant.parse("2026-09-01T03:00:00Z").plus(day, ChronoUnit.DAYS).toString());
            bothAnswer(round, 1L, 1L);
            roundIds.addFirst(round.getId().toString());
        });

        // when: 첫 페이지를 받은 뒤 이번 회차에 서로 답하고 다음 페이지를 받는다
        String firstPage = body(history(me, partner, null).andExpect(status().isOk()));
        String currentRoundId = JsonPath.read(body(current(me, partner)), "$.roundId");
        BalanceGameRound currentRound = balanceGameRoundRepository.findById(Long.parseLong(currentRoundId)).orElseThrow();
        bothAnswer(currentRound, 1L, 1L);
        String secondPage = body(history(me, partner, JsonPath.read(firstPage, "$.nextCursor")).andExpect(status().isOk()));

        // then: 첫 페이지 20개, 다음 페이지는 남은 1개뿐이고 더 이전 회차가 없다
        assertThat(JsonPath.<List<String>>read(firstPage, "$.questions[*].roundId")).isEqualTo(roundIds.subList(0, 20));
        assertThat(JsonPath.<String>read(firstPage, "$.nextCursor")).isEqualTo(roundIds.get(19));
        assertThat(JsonPath.<List<String>>read(secondPage, "$.questions[*].roundId")).containsExactly(roundIds.get(20));
        assertThat(JsonPath.<Object>read(secondPage, "$.nextCursor")).isNull();

        // then: 새로 생긴 회차는 처음부터 다시 받을 때 맨 앞에 나온다
        history(me, partner, null).andExpect(jsonPath("$.questions[0].roundId").value(currentRoundId));
    }

    @Test
    @DisplayName("서로 답한 회차가 없으면 기록은 빈 목록과 null cursor 다")
    void history_is_empty_without_comparison() throws Exception {
        // when & then: 서로 답한 회차가 없는 트윈과의 기록을 조회하면 200 + 빈 목록·null cursor
        history(me, partner, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.questions").isEmpty())
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    @DisplayName("없는 회차를 cursor 로 주면 400 INVALID_REQUEST, 차단한 상대의 기록은 404 RELATIONSHIP_NOT_FOUND 다")
    void history_rejects_unknown_cursor_and_blocked_partner() throws Exception {
        // given: 내가 다른 트윈을 차단했다
        blockRepository.save(Block.create(me.getId(), other.getId()));

        // when & then: 없는 회차 cursor 는 400, 차단한 상대의 기록은 404
        history(me, partner, "999999999")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        history(me, other, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RELATIONSHIP_NOT_FOUND"));
    }

    @Test
    @DisplayName("인증 헤더 없이 기록을 조회하면 401 이다")
    void history_without_auth_returns_401() throws Exception {
        // given: 트윈과 서로 답한 회차가 있다
        bothAnswer(pastRound("2026-09-01T03:00:00Z"), 1L, 1L);

        // when & then: 토큰 없이 조회하면 401
        mockMvc.perform(get("/api/v1/people/{userId}/intimacy-quiz/history", partner.getId().toString()))
                .andExpect(status().isUnauthorized());
    }

    private BalanceGameRound pastRound(String startsAt) {
        return pastRound(startsAt, season);
    }

    private BalanceGameRound pastRound(String startsAt, Season roundSeason) {
        balanceGameRoundRepository.upsert(Instant.parse(startsAt), 1L, roundSeason.getId());
        return balanceGameRoundRepository.findByStartsAt(Instant.parse(startsAt)).orElseThrow();
    }

    private void bothAnswer(BalanceGameRound round, Long myOptionId, Long partnerOptionId) {
        balanceGameAnswerRepository.saveAll(List.of(
                BalanceGameAnswer.create(round.getId(), me.getId(), partner.getId(), myOptionId),
                BalanceGameAnswer.create(round.getId(), partner.getId(), me.getId(), partnerOptionId)));
    }

    private ResultActions history(User user, User partner, String cursor) throws Exception {
        var request = get("/api/v1/people/{userId}/intimacy-quiz/history", partner.getId().toString())
                .header("Authorization", bearer(user.getId()));
        if (cursor != null) {
            request.param("cursor", cursor);
        }
        return mockMvc.perform(request);
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

    private ResultActions feeds(User user) throws Exception {
        return mockMvc.perform(get("/api/v1/me/app-notifications/feeds")
                .param("type", "balanceGameAnswered")
                .header("Authorization", bearer(user.getId())));
    }

    private ResultActions profile(User user, User partner) throws Exception {
        return mockMvc.perform(get("/api/v1/people/{userId}/profile", partner.getId().toString())
                .header("Authorization", bearer(user.getId())));
    }
}
