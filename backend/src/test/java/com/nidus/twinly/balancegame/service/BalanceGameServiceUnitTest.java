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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class BalanceGameServiceUnitTest {

    private static final Long ME = 10L;
    private static final Long PARTNER = 20L;
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

    // ---------------------------------------------------------------- 이번 회차 조회

    @Test
    @DisplayName("이번 회차(KST 12시·18시 출제)를 모두가 공유하도록 만들고, 이 트윈에게 아직 안 답했으면 내 답을 기다린다")
    void current_creates_shared_round_and_waits_for_me() {
        // given: 사람 목록에 있는 트윈
        Instant startsAt = givenVisiblePartnerAndRound();
        givenAnswers(null, null);

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
    @DisplayName("트윈이 나에게 먼저 답했어도 내가 답하기 전에는 트윈의 선택을 보여주지 않는다")
    void current_hides_partner_option_until_i_answer() {
        // given: 트윈만 나에게 2번
        givenVisiblePartnerAndRound();
        givenAnswers(null, 2L);

        // when
        BalanceGameResult result = balanceGameService.current(ME, PARTNER);

        // then
        assertThat(result.status()).isEqualTo(BalanceGameStatus.WAITING_ME);
        assertThat(result.partnerAnswered()).isTrue();
        assertThat(result.partnerOptionId()).isNull();
    }

    @Test
    @DisplayName("나만 이 트윈에게 답했으면 트윈을 기다린다")
    void current_waits_for_partner_after_my_answer() {
        // given: 나만 트윈에게 1번
        givenVisiblePartnerAndRound();
        givenAnswers(1L, null);

        // when
        BalanceGameResult result = balanceGameService.current(ME, PARTNER);

        // then
        assertThat(result.status()).isEqualTo(BalanceGameStatus.WAITING_PARTNER);
        assertThat(result.myOptionId()).isEqualTo(1L);
        assertThat(result.intimacyBonus()).isZero();
    }

    @Test
    @DisplayName("서로에게 같은 답을 했으면 일치 결과와 트윈의 선택, 오른 점수를 보여준다")
    void current_shows_matched_result() {
        // given: 서로에게 2번
        givenVisiblePartnerAndRound();
        givenAnswers(2L, 2L);

        // when
        BalanceGameResult result = balanceGameService.current(ME, PARTNER);

        // then
        assertThat(result.status()).isEqualTo(BalanceGameStatus.MATCHED);
        assertThat(result.partnerOptionId()).isEqualTo(2L);
        assertThat(result.intimacyBonus()).isEqualTo(2);
    }

    @Test
    @DisplayName("서로에게 다른 답을 했으면 불일치 결과를 보여주고 점수는 없다")
    void current_shows_mismatched_result() {
        // given: 나는 1번, 트윈은 2번
        givenVisiblePartnerAndRound();
        givenAnswers(1L, 2L);

        // when
        BalanceGameResult result = balanceGameService.current(ME, PARTNER);

        // then
        assertThat(result.status()).isEqualTo(BalanceGameStatus.MISMATCHED);
        assertThat(result.partnerOptionId()).isEqualTo(2L);
        assertThat(result.intimacyBonus()).isZero();
    }

    @Test
    @DisplayName("트윈이 없거나 탈퇴했으면 USER_NOT_FOUND 이고 회차를 만들지 않는다")
    void current_for_withdrawn_partner_throws() {
        // given: 탈퇴 신청한 트윈
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
    @DisplayName("트윈이 나를 차단했으면 RELATIONSHIP_NOT_FOUND 다")
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
    @DisplayName("트윈에게 먼저 답하면 내 답만 남기고 점수는 쌓지 않은 채 트윈을 기다린다")
    void answer_first_waits_for_partner() {
        // given: 아직 서로 안 답한 회차
        givenOpenRoundAndVisiblePartner();
        givenAnswers(null, null);

        // when: 트윈에게 1번
        BalanceGameResult result = balanceGameService.answer(ME, ROUND_ID, new BalanceGameAnswerCommand(PARTNER, 1L));

        // then: 이 트윈에게 한 답 하나를 남긴다
        ArgumentCaptor<BalanceGameAnswer> answer = ArgumentCaptor.forClass(BalanceGameAnswer.class);
        then(balanceGameAnswerRepository).should().save(answer.capture());
        assertThat(answer.getValue().getRoundId()).isEqualTo(ROUND_ID);
        assertThat(answer.getValue().getUserId()).isEqualTo(ME);
        assertThat(answer.getValue().getPartnerUserId()).isEqualTo(PARTNER);
        assertThat(answer.getValue().getOptionId()).isEqualTo(1L);
        then(intimacyBonusRepository).should(never()).save(any());
        assertThat(result.partnerId()).isEqualTo(PARTNER);
    }

    @Test
    @DisplayName("트윈이 나에게 한 답과 같은 답을 하면 쌍에 2점을 쌓는다")
    void answer_matching_partner_answer_grants_bonus() {
        // given: 트윈이 나에게 이미 2번, 점수를 더해도 70 미만
        givenOpenRoundAndVisiblePartner();
        givenAnswers(null, 2L);
        given(intimacyReader.read(any(), any(), any(LocalDateTime.class))).willReturn(new Intimacy(42, 2));

        // when: 나도 트윈에게 2번
        balanceGameService.answer(ME, ROUND_ID, new BalanceGameAnswerCommand(PARTNER, 2L));

        // then: 쌍 단위 2점 한 건, 70 미만이라 채팅방은 열지 않는다
        ArgumentCaptor<IntimacyBonus> bonus = ArgumentCaptor.forClass(IntimacyBonus.class);
        then(intimacyBonusRepository).should().save(bonus.capture());
        assertThat(bonus.getValue().getUserAId()).isEqualTo(ME);
        assertThat(bonus.getValue().getUserBId()).isEqualTo(PARTNER);
        assertThat(bonus.getValue().getAmount()).isEqualTo(2);
        then(chatRoomOpener).should(never()).open(any(), any());
    }

    @Test
    @DisplayName("트윈이 나에게 한 답과 다르면 점수를 쌓지 않는다")
    void answer_different_from_partner_answer_grants_nothing() {
        // given: 트윈이 나에게 이미 2번
        givenOpenRoundAndVisiblePartner();
        givenAnswers(null, 2L);

        // when: 나는 트윈에게 1번
        balanceGameService.answer(ME, ROUND_ID, new BalanceGameAnswerCommand(PARTNER, 1L));

        // then
        then(intimacyBonusRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("게임 점수로 어느 한쪽 친밀도가 70 이 되면 그 자리에서 채팅방을 연다")
    void answer_opens_chat_room_at_best_friend() {
        // given: 같은 답을 하면 트윈 쪽 친밀도가 70 이 된다
        givenOpenRoundAndVisiblePartner();
        givenAnswers(null, 1L);
        given(intimacyReader.read(eq(ME), eq(PARTNER), any(LocalDateTime.class))).willReturn(new Intimacy(69, 2));
        given(intimacyReader.read(eq(PARTNER), eq(ME), any(LocalDateTime.class))).willReturn(new Intimacy(70, 2));

        // when
        balanceGameService.answer(ME, ROUND_ID, new BalanceGameAnswerCommand(PARTNER, 1L));

        // then
        then(chatRoomOpener).should().open(ME, PARTNER);
    }

    @Test
    @DisplayName("없는 회차면 INTIMACY_QUIZ_NOT_FOUND 다")
    void answer_to_unknown_round_throws() {
        // given
        given(balanceGameRoundRepository.findByIdForUpdate(ROUND_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> balanceGameService.answer(ME, ROUND_ID, new BalanceGameAnswerCommand(PARTNER, 1L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INTIMACY_QUIZ_NOT_FOUND);
    }

    @Test
    @DisplayName("사람 목록에 없는 상대에게는 답할 수 없다 (RELATIONSHIP_NOT_FOUND)")
    void answer_to_stranger_throws() {
        // given: 열린 회차, 관계 기록이 없는 상대
        given(balanceGameRoundRepository.findByIdForUpdate(ROUND_ID)).willReturn(Optional.of(round(thisRound())));
        given(userRepository.findById(PARTNER)).willReturn(Optional.of(user(PARTNER)));
        given(relationshipRepository.findLatestUntilByUserIdAndPartnerUserId(eq(ME), eq(PARTNER), any(LocalDateTime.class)))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> balanceGameService.answer(ME, ROUND_ID, new BalanceGameAnswerCommand(PARTNER, 1L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RELATIONSHIP_NOT_FOUND);
        then(balanceGameAnswerRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("질문에 없는 선택지는 INVALID_REQUEST 이고 답을 남기지 않는다")
    void answer_with_unknown_option_throws() {
        // given
        givenOpenRoundAndVisiblePartner();

        // when & then
        assertThatThrownBy(() -> balanceGameService.answer(ME, ROUND_ID, new BalanceGameAnswerCommand(PARTNER, 3L)))
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
        givenVisiblePartner();
        given(balanceGameQuestionLoader.findQuestion(7L)).willReturn(Optional.of(QUESTION));

        // when & then
        assertThatThrownBy(() -> balanceGameService.answer(ME, ROUND_ID, new BalanceGameAnswerCommand(PARTNER, 1L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INTIMACY_QUIZ_EXPIRED);
        then(balanceGameAnswerRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("이번 회차에 이 트윈에게 이미 답했으면 INTIMACY_QUIZ_ALREADY_ANSWERED 이고 점수도 다시 쌓지 않는다")
    void answer_twice_to_same_partner_throws() {
        // given: 이 트윈에게 이미 1번
        givenOpenRoundAndVisiblePartner();
        given(balanceGameAnswerRepository.findByRoundIdAndUserIdAndPartnerUserId(ROUND_ID, ME, PARTNER))
                .willReturn(Optional.of(answer(ME, PARTNER, 1L)));

        // when & then
        assertThatThrownBy(() -> balanceGameService.answer(ME, ROUND_ID, new BalanceGameAnswerCommand(PARTNER, 2L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INTIMACY_QUIZ_ALREADY_ANSWERED);
        then(balanceGameAnswerRepository).should(never()).save(any());
        then(intimacyBonusRepository).should(never()).save(any());
    }

    private Instant givenVisiblePartnerAndRound() {
        givenVisiblePartner();
        Instant startsAt = thisRound();
        given(balanceGameQuestionLoader.questionFor(BalanceGameSchedule.sequenceOf(startsAt))).willReturn(QUESTION);
        given(balanceGameQuestionLoader.findQuestion(7L)).willReturn(Optional.of(QUESTION));
        given(balanceGameRoundRepository.findByStartsAt(startsAt)).willReturn(Optional.of(round(startsAt)));
        return startsAt;
    }

    private void givenOpenRoundAndVisiblePartner() {
        given(balanceGameRoundRepository.findByIdForUpdate(ROUND_ID)).willReturn(Optional.of(round(thisRound())));
        givenVisiblePartner();
        given(balanceGameQuestionLoader.findQuestion(7L)).willReturn(Optional.of(QUESTION));
    }

    private void givenVisiblePartner() {
        given(userRepository.findById(PARTNER)).willReturn(Optional.of(user(PARTNER)));
        given(relationshipRepository.findLatestUntilByUserIdAndPartnerUserId(eq(ME), eq(PARTNER), any(LocalDateTime.class)))
                .willReturn(Optional.of(BeanUtils.instantiateClass(Relationship.class)));
    }

    private void givenAnswers(Long myOptionId, Long partnerOptionId) {
        given(balanceGameAnswerRepository.findByRoundIdAndUserIdAndPartnerUserId(ROUND_ID, ME, PARTNER))
                .willReturn(Optional.ofNullable(myOptionId).map(optionId -> answer(ME, PARTNER, optionId)));
        given(balanceGameAnswerRepository.findByRoundIdAndUserIdAndPartnerUserId(ROUND_ID, PARTNER, ME))
                .willReturn(Optional.ofNullable(partnerOptionId).map(optionId -> answer(PARTNER, ME, optionId)));
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

    private BalanceGameAnswer answer(Long userId, Long partnerUserId, Long optionId) {
        return BalanceGameAnswer.create(ROUND_ID, userId, partnerUserId, optionId);
    }

    private User user(Long id) {
        User user = BeanUtils.instantiateClass(User.class);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
