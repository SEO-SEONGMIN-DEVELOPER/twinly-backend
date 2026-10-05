package com.nidus.twinly.balancegame.service;

import com.nidus.twinly.balancegame.domain.BalanceGameSchedule;
import com.nidus.twinly.balancegame.domain.BalanceGameStatus;
import com.nidus.twinly.balancegame.dto.command.BalanceGameAnswerCommand;
import com.nidus.twinly.balancegame.dto.result.BalanceGameResult;
import com.nidus.twinly.balancegame.entity.BalanceGameAnswer;
import com.nidus.twinly.balancegame.entity.BalanceGameRound;
import com.nidus.twinly.balancegame.repository.BalanceGameAnswerRepository;
import com.nidus.twinly.balancegame.repository.BalanceGameRoundRepository;
import com.nidus.twinly.block.repository.BlockRepository;
import com.nidus.twinly.chat.opener.ChatRoomOpener;
import com.nidus.twinly.common.balancegame.BalanceGameOption;
import com.nidus.twinly.common.balancegame.BalanceGameQuestion;
import com.nidus.twinly.common.balancegame.BalanceGameQuestionLoader;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.relationship.domain.Intimacy;
import com.nidus.twinly.relationship.entity.IntimacyBonus;
import com.nidus.twinly.relationship.entity.Relationship;
import com.nidus.twinly.relationship.reader.IntimacyReader;
import com.nidus.twinly.relationship.repository.IntimacyBonusRepository;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class BalanceGameServiceUnitTest {

    private static final Long ME = 10L;
    private static final Long PARTNER = 20L;
    private static final Long OTHER = 30L;
    private static final Long ROUND_ID = 100L;
    private static final BalanceGameQuestion QUESTION = new BalanceGameQuestion(7L, "평생 하나만 먹어야 한다면?",
            List.of(new BalanceGameOption(1L, "평생 라면"), new BalanceGameOption(2L, "평생 치킨")));

    @Mock
    BalanceGameRoundRepository balanceGameRoundRepository;

    @Mock
    BalanceGameAnswerRepository balanceGameAnswerRepository;

    @Mock
    IntimacyBonusRepository intimacyBonusRepository;

    @Mock
    RelationshipRepository relationshipRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    BlockRepository blockRepository;

    @Mock
    BalanceGameQuestionLoader balanceGameQuestionLoader;

    @Mock
    IntimacyReader intimacyReader;

    @Mock
    ChatRoomOpener chatRoomOpener;

    @InjectMocks
    BalanceGameService balanceGameService;

    // ---------------------------------------------------------------- 이번 시간 조회

    @Test
    @DisplayName("이번 회차(KST 12시·18시 출제)를 모두가 공유하도록 만들고, 아직 아무도 안 답했으면 내 답을 기다린다")
    void current_creates_shared_round_of_this_hour() {
        // given: 사람 목록에 있는 상대
        Instant startsAt = givenVisiblePartnerAndRound();

        // when
        BalanceGameResult result = balanceGameService.current(ME, PARTNER);

        // then: 이번 회차를 (이미 있으면 그대로) 만들고 공통 질문을 담는다
        then(balanceGameRoundRepository).should().upsert(startsAt, 7L);
        assertThat(result.roundId()).isEqualTo(ROUND_ID);
        assertThat(result.partnerId()).isEqualTo(PARTNER);
        assertThat(result.question().text()).isEqualTo("평생 하나만 먹어야 한다면?");
        assertThat(result.endsAt()).isEqualTo(BalanceGameSchedule.nextRoundStartOf(startsAt));
        assertThat(result.status()).isEqualTo(BalanceGameStatus.WAITING_ME);
        assertThat(result.myOptionId()).isNull();
        assertThat(result.partnerAnswered()).isFalse();
    }

    @Test
    @DisplayName("상대가 먼저 답했어도 내가 답하기 전에는 상대의 선택을 보여주지 않는다")
    void current_hides_partner_option_until_i_answer() {
        // given: 상대만 2번
        givenVisiblePartnerAndRound();
        given(balanceGameAnswerRepository.findByRoundIdAndUserId(ROUND_ID, ME)).willReturn(Optional.empty());
        given(balanceGameAnswerRepository.findByRoundIdAndUserId(ROUND_ID, PARTNER)).willReturn(Optional.of(answer(PARTNER, 2L)));

        // when
        BalanceGameResult result = balanceGameService.current(ME, PARTNER);

        // then
        assertThat(result.status()).isEqualTo(BalanceGameStatus.WAITING_ME);
        assertThat(result.partnerAnswered()).isTrue();
        assertThat(result.partnerOptionId()).isNull();
    }

    @Test
    @DisplayName("나만 답했으면 상대를 기다린다")
    void current_waits_for_partner_after_my_answer() {
        // given: 나만 1번
        givenVisiblePartnerAndRound();
        given(balanceGameAnswerRepository.findByRoundIdAndUserId(ROUND_ID, ME)).willReturn(Optional.of(answer(ME, 1L)));

        // when
        BalanceGameResult result = balanceGameService.current(ME, PARTNER);

        // then
        assertThat(result.status()).isEqualTo(BalanceGameStatus.WAITING_PARTNER);
        assertThat(result.myOptionId()).isEqualTo(1L);
        assertThat(result.intimacyBonus()).isZero();
    }

    @Test
    @DisplayName("둘 다 같은 답이면 일치 결과와 상대의 선택, 오른 점수를 보여준다")
    void current_shows_matched_result() {
        // given: 둘 다 2번
        givenVisiblePartnerAndRound();
        given(balanceGameAnswerRepository.findByRoundIdAndUserId(ROUND_ID, ME)).willReturn(Optional.of(answer(ME, 2L)));
        given(balanceGameAnswerRepository.findByRoundIdAndUserId(ROUND_ID, PARTNER)).willReturn(Optional.of(answer(PARTNER, 2L)));

        // when
        BalanceGameResult result = balanceGameService.current(ME, PARTNER);

        // then
        assertThat(result.status()).isEqualTo(BalanceGameStatus.MATCHED);
        assertThat(result.partnerOptionId()).isEqualTo(2L);
        assertThat(result.intimacyBonus()).isEqualTo(2);
    }

    @Test
    @DisplayName("둘 다 답했지만 다르면 불일치 결과를 보여주고 점수는 없다")
    void current_shows_mismatched_result() {
        // given: 나는 1번, 상대는 2번
        givenVisiblePartnerAndRound();
        given(balanceGameAnswerRepository.findByRoundIdAndUserId(ROUND_ID, ME)).willReturn(Optional.of(answer(ME, 1L)));
        given(balanceGameAnswerRepository.findByRoundIdAndUserId(ROUND_ID, PARTNER)).willReturn(Optional.of(answer(PARTNER, 2L)));

        // when
        BalanceGameResult result = balanceGameService.current(ME, PARTNER);

        // then
        assertThat(result.status()).isEqualTo(BalanceGameStatus.MISMATCHED);
        assertThat(result.partnerOptionId()).isEqualTo(2L);
        assertThat(result.intimacyBonus()).isZero();
    }

    @Test
    @DisplayName("상대가 없거나 탈퇴했으면 USER_NOT_FOUND 이고 회차를 만들지 않는다")
    void current_for_withdrawn_partner_throws() {
        // given: 탈퇴 신청한 상대
        User partner = user(PARTNER);
        ReflectionTestUtils.setField(partner, "withdrawalRequestedAt", Instant.now());
        given(userRepository.findById(PARTNER)).willReturn(Optional.of(partner));

        // when & then
        assertThatThrownBy(() -> balanceGameService.current(ME, PARTNER))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
        then(balanceGameRoundRepository).should(never()).upsert(any(), any());
    }

    @Test
    @DisplayName("사람 목록에 없는 상대는 RELATIONSHIP_NOT_FOUND 다")
    void current_without_relationship_throws() {
        // given: 보이는 관계 기록이 없는 상대
        given(userRepository.findById(PARTNER)).willReturn(Optional.of(user(PARTNER)));
        given(relationshipRepository.findLatestUntilByUserIdAndPartnerUserId(eq(ME), eq(PARTNER), any(LocalDateTime.class)))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> balanceGameService.current(ME, PARTNER))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RELATIONSHIP_NOT_FOUND);
    }

    @Test
    @DisplayName("상대가 나를 차단했으면 RELATIONSHIP_NOT_FOUND 다")
    void current_blocked_by_partner_throws() {
        // given
        given(userRepository.findById(PARTNER)).willReturn(Optional.of(user(PARTNER)));
        given(relationshipRepository.findLatestUntilByUserIdAndPartnerUserId(eq(ME), eq(PARTNER), any(LocalDateTime.class)))
                .willReturn(Optional.of(BeanUtils.instantiateClass(Relationship.class)));
        given(blockRepository.existsByUserIdAndBlockedUserId(ME, PARTNER)).willReturn(false);
        given(blockRepository.existsByUserIdAndBlockedUserId(PARTNER, ME)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> balanceGameService.current(ME, PARTNER))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RELATIONSHIP_NOT_FOUND);
    }

    // ---------------------------------------------------------------- 답변

    @Test
    @DisplayName("한 번 답하면 이미 같은 답을 고른 목록의 상대마다 쌍에 2점씩 쌓는다")
    void answer_grants_bonus_to_every_matched_partner() {
        // given: 같은 2번을 먼저 고른 상대 둘, 점수를 더해도 70 미만
        givenOpenRound();
        given(balanceGameAnswerRepository.findMatchedPartnerUserIds(eq(ROUND_ID), eq(2L), eq(ME), any(LocalDateTime.class)))
                .willReturn(List.of(PARTNER, OTHER));
        given(intimacyReader.read(anyLong(), anyLong(), any(LocalDateTime.class))).willReturn(new Intimacy(42, 2));

        // when: 내가 2번
        balanceGameService.answer(ME, ROUND_ID, new BalanceGameAnswerCommand(2L));

        // then: 내 답 하나를 남기고
        ArgumentCaptor<BalanceGameAnswer> answer = ArgumentCaptor.forClass(BalanceGameAnswer.class);
        then(balanceGameAnswerRepository).should().save(answer.capture());
        assertThat(answer.getValue().getRoundId()).isEqualTo(ROUND_ID);
        assertThat(answer.getValue().getUserId()).isEqualTo(ME);
        assertThat(answer.getValue().getOptionId()).isEqualTo(2L);

        // then: 일치한 두 쌍에 각각 2점, 70 미만이라 채팅방은 열지 않는다
        ArgumentCaptor<IntimacyBonus> bonuses = ArgumentCaptor.forClass(IntimacyBonus.class);
        then(intimacyBonusRepository).should(times(2)).save(bonuses.capture());
        assertThat(bonuses.getAllValues())
                .extracting(IntimacyBonus::getUserAId, IntimacyBonus::getUserBId, IntimacyBonus::getAmount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(ME, PARTNER, 2),
                        org.assertj.core.groups.Tuple.tuple(ME, OTHER, 2));
        then(chatRoomOpener).should(never()).open(any(), any());
    }

    @Test
    @DisplayName("아직 같은 답을 고른 상대가 없으면 답만 남기고 점수는 쌓지 않는다")
    void answer_without_match_grants_nothing() {
        // given
        givenOpenRound();
        given(balanceGameAnswerRepository.findMatchedPartnerUserIds(eq(ROUND_ID), eq(1L), eq(ME), any(LocalDateTime.class)))
                .willReturn(List.of());

        // when
        balanceGameService.answer(ME, ROUND_ID, new BalanceGameAnswerCommand(1L));

        // then
        then(balanceGameAnswerRepository).should().save(any());
        then(intimacyBonusRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("게임 점수로 어느 한쪽 친밀도가 70 이 되면 그 자리에서 채팅방을 연다")
    void answer_opens_chat_room_at_best_friend() {
        // given: 같은 답을 고른 상대 쪽 친밀도가 70 이 된다
        givenOpenRound();
        given(balanceGameAnswerRepository.findMatchedPartnerUserIds(eq(ROUND_ID), eq(1L), eq(ME), any(LocalDateTime.class)))
                .willReturn(List.of(PARTNER));
        given(intimacyReader.read(eq(ME), eq(PARTNER), any(LocalDateTime.class))).willReturn(new Intimacy(69, 2));
        given(intimacyReader.read(eq(PARTNER), eq(ME), any(LocalDateTime.class))).willReturn(new Intimacy(70, 2));

        // when
        balanceGameService.answer(ME, ROUND_ID, new BalanceGameAnswerCommand(1L));

        // then
        then(chatRoomOpener).should().open(ME, PARTNER);
    }

    @Test
    @DisplayName("없는 회차면 INTIMACY_QUIZ_NOT_FOUND 다")
    void answer_to_unknown_round_throws() {
        // given
        given(balanceGameRoundRepository.findByIdForUpdate(ROUND_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> balanceGameService.answer(ME, ROUND_ID, new BalanceGameAnswerCommand(1L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INTIMACY_QUIZ_NOT_FOUND);
    }

    @Test
    @DisplayName("질문에 없는 선택지는 INVALID_REQUEST 이고 답을 남기지 않는다")
    void answer_with_unknown_option_throws() {
        // given
        given(balanceGameRoundRepository.findByIdForUpdate(ROUND_ID)).willReturn(Optional.of(round(thisRound())));
        given(balanceGameQuestionLoader.findQuestion(7L)).willReturn(Optional.of(QUESTION));

        // when & then
        assertThatThrownBy(() -> balanceGameService.answer(ME, ROUND_ID, new BalanceGameAnswerCommand(3L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        then(balanceGameAnswerRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("끝난 회차에는 답할 수 없다 (INTIMACY_QUIZ_EXPIRED)")
    void answer_after_end_throws() {
        // given: 2026-01-01 12시 회차
        given(balanceGameRoundRepository.findByIdForUpdate(ROUND_ID)).willReturn(Optional.of(round(Instant.parse("2026-01-01T03:00:00Z"))));
        given(balanceGameQuestionLoader.findQuestion(7L)).willReturn(Optional.of(QUESTION));

        // when & then
        assertThatThrownBy(() -> balanceGameService.answer(ME, ROUND_ID, new BalanceGameAnswerCommand(1L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INTIMACY_QUIZ_EXPIRED);
        then(balanceGameAnswerRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("이미 답한 회차에 다시 답하면 INTIMACY_QUIZ_ALREADY_ANSWERED 이고 점수도 다시 쌓지 않는다")
    void answer_twice_throws() {
        // given: 이번 회차에 이미 1번
        givenOpenRoundWithoutAnswer();
        given(balanceGameAnswerRepository.findByRoundIdAndUserId(ROUND_ID, ME)).willReturn(Optional.of(answer(ME, 1L)));

        // when & then
        assertThatThrownBy(() -> balanceGameService.answer(ME, ROUND_ID, new BalanceGameAnswerCommand(2L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INTIMACY_QUIZ_ALREADY_ANSWERED);
        then(balanceGameAnswerRepository).should(never()).save(any());
        then(intimacyBonusRepository).should(never()).save(any());
    }

    private Instant givenVisiblePartnerAndRound() {
        given(userRepository.findById(PARTNER)).willReturn(Optional.of(user(PARTNER)));
        given(relationshipRepository.findLatestUntilByUserIdAndPartnerUserId(eq(ME), eq(PARTNER), any(LocalDateTime.class)))
                .willReturn(Optional.of(BeanUtils.instantiateClass(Relationship.class)));
        Instant startsAt = thisRound();
        given(balanceGameQuestionLoader.questionFor(BalanceGameSchedule.sequenceOf(startsAt))).willReturn(QUESTION);
        given(balanceGameQuestionLoader.findQuestion(7L)).willReturn(Optional.of(QUESTION));
        given(balanceGameRoundRepository.findByStartsAt(startsAt)).willReturn(Optional.of(round(startsAt)));
        return startsAt;
    }

    private void givenOpenRound() {
        givenOpenRoundWithoutAnswer();
        given(balanceGameAnswerRepository.findByRoundIdAndUserId(ROUND_ID, ME)).willReturn(Optional.empty());
    }

    private void givenOpenRoundWithoutAnswer() {
        given(balanceGameRoundRepository.findByIdForUpdate(ROUND_ID)).willReturn(Optional.of(round(thisRound())));
        given(balanceGameQuestionLoader.findQuestion(7L)).willReturn(Optional.of(QUESTION));
    }

    private Instant thisRound() {
        return BalanceGameSchedule.roundStartOf(KstTimes.now());
    }

    private BalanceGameRound round(Instant startsAt) {
        BalanceGameRound round = BeanUtils.instantiateClass(BalanceGameRound.class);
        ReflectionTestUtils.setField(round, "id", ROUND_ID);
        ReflectionTestUtils.setField(round, "startsAt", startsAt);
        ReflectionTestUtils.setField(round, "questionId", 7L);
        return round;
    }

    private BalanceGameAnswer answer(Long userId, Long optionId) {
        return BalanceGameAnswer.create(ROUND_ID, userId, optionId);
    }

    private User user(Long id) {
        User user = BeanUtils.instantiateClass(User.class);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
