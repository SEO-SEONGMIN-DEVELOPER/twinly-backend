package com.nidus.twinly.balancegame.service;

import com.nidus.twinly.balancegame.domain.BalanceGameSchedule;
import com.nidus.twinly.balancegame.domain.BalanceGameStatus;
import com.nidus.twinly.balancegame.dto.command.BalanceGameAnswerCommand;
import com.nidus.twinly.balancegame.dto.result.BalanceGameQuestionResult;
import com.nidus.twinly.balancegame.dto.result.BalanceGameResult;
import com.nidus.twinly.balancegame.entity.BalanceGameAnswer;
import com.nidus.twinly.balancegame.entity.BalanceGameRound;
import com.nidus.twinly.balancegame.repository.BalanceGameAnswerRepository;
import com.nidus.twinly.balancegame.repository.BalanceGameRoundRepository;
import com.nidus.twinly.block.repository.BlockRepository;
import com.nidus.twinly.chat.opener.ChatRoomOpener;
import com.nidus.twinly.common.balancegame.BalanceGameQuestion;
import com.nidus.twinly.common.balancegame.BalanceGameQuestionLoader;
import com.nidus.twinly.common.logging.InfoLog;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.relationship.domain.RelationshipType;
import com.nidus.twinly.relationship.entity.IntimacyBonus;
import com.nidus.twinly.relationship.reader.IntimacyReader;
import com.nidus.twinly.relationship.repository.IntimacyBonusRepository;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Stream;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class BalanceGameService {

    private static final int MATCH_BONUS = 10;

    private final BalanceGameRoundRepository balanceGameRoundRepository;
    private final BalanceGameAnswerRepository balanceGameAnswerRepository;
    private final IntimacyBonusRepository intimacyBonusRepository;
    private final RelationshipRepository relationshipRepository;
    private final UserRepository userRepository;
    private final BlockRepository blockRepository;
    private final BalanceGameQuestionLoader balanceGameQuestionLoader;
    private final IntimacyReader intimacyReader;
    private final ChatRoomOpener chatRoomOpener;

    public BalanceGameResult current(Long userId, Long partnerUserId) {
        LocalDateTime now = KstTimes.now();
        validatePartner(userId, partnerUserId, now);

        return toResult(currentRound(now), userId, partnerUserId);
    }

    public BalanceGameResult answer(Long userId, Long roundId, BalanceGameAnswerCommand command) {
        BalanceGameRound round = balanceGameRoundRepository.findByIdForUpdate(roundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTIMACY_QUIZ_NOT_FOUND));
        Long partnerUserId = command.partnerId();
        validatePartner(userId, partnerUserId, KstTimes.now());
        if (!questionOf(round).hasOption(command.optionId())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (round.isEnded(Instant.now())) {
            throw new BusinessException(ErrorCode.INTIMACY_QUIZ_EXPIRED);
        }
        if (optionIdOf(round, userId, partnerUserId).isPresent()) {
            throw new BusinessException(ErrorCode.INTIMACY_QUIZ_ALREADY_ANSWERED);
        }

        balanceGameAnswerRepository.save(BalanceGameAnswer.create(roundId, userId, partnerUserId, command.optionId()));

        if (optionIdOf(round, partnerUserId, userId).filter(command.optionId()::equals).isPresent()) {
            intimacyBonusRepository.save(IntimacyBonus.create(userId, partnerUserId, MATCH_BONUS));
            openChatRoomIfBestFriend(userId, partnerUserId);
        }

        return toResult(round, userId, partnerUserId);
    }

    private void validatePartner(Long userId, Long partnerUserId, LocalDateTime now) {
        User partner = userRepository.findById(partnerUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (partner.isWithdrawn()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (relationshipRepository.findLatestUntilByUserIdAndPartnerUserId(userId, partnerUserId, now).isEmpty()
                || isBlockedEitherWay(userId, partnerUserId)) {
            throw new BusinessException(ErrorCode.RELATIONSHIP_NOT_FOUND);
        }
    }

    private BalanceGameResult toResult(BalanceGameRound round, Long userId, Long partnerUserId) {
        Optional<Long> myOptionId = optionIdOf(round, userId, partnerUserId);
        Optional<Long> partnerOptionId = optionIdOf(round, partnerUserId, userId);
        boolean completed = myOptionId.isPresent() && partnerOptionId.isPresent();
        boolean matched = completed && myOptionId.get().equals(partnerOptionId.get());

        return new BalanceGameResult(
                round.getId(),
                partnerUserId,
                BalanceGameQuestionResult.from(questionOf(round)),
                round.endsAt(),
                status(myOptionId.isPresent(), completed, matched),
                myOptionId.orElse(null),
                completed ? partnerOptionId.get() : null,
                partnerOptionId.isPresent(),
                matched ? MATCH_BONUS : 0
        );
    }

    private BalanceGameRound currentRound(LocalDateTime now) {
        Instant startsAt = BalanceGameSchedule.roundStartOf(now);

        balanceGameRoundRepository.upsert(startsAt,
                balanceGameQuestionLoader.questionFor(BalanceGameSchedule.sequenceOf(startsAt)).id());

        return balanceGameRoundRepository.findByStartsAt(startsAt)
                .orElseThrow(() -> new IllegalStateException("방금 만든 밸런스 게임 회차를 찾을 수 없습니다."));
    }

    private Optional<Long> optionIdOf(BalanceGameRound round, Long userId, Long partnerUserId) {
        return balanceGameAnswerRepository.findByRoundIdAndUserIdAndPartnerUserId(round.getId(), userId, partnerUserId)
                .map(BalanceGameAnswer::getOptionId);
    }

    private BalanceGameStatus status(boolean answered, boolean completed, boolean matched) {
        if (completed) {
            return matched ? BalanceGameStatus.MATCHED : BalanceGameStatus.MISMATCHED;
        }

        return answered ? BalanceGameStatus.WAITING_PARTNER : BalanceGameStatus.WAITING_ME;
    }

    private void openChatRoomIfBestFriend(Long userId, Long partnerUserId) {
        LocalDateTime now = KstTimes.now();
        boolean bestFriend = Stream.of(
                        intimacyReader.read(userId, partnerUserId, now),
                        intimacyReader.read(partnerUserId, userId, now))
                .anyMatch(intimacy -> RelationshipType.fromIntimacy(intimacy.value()) == RelationshipType.BEST_FRIEND);

        if (!bestFriend) {
            return;
        }

        try {
            chatRoomOpener.open(userId, partnerUserId);
        } catch (DataIntegrityViolationException e) {
            InfoLog.log(log, "상대 쪽에서 채팅방을 먼저 열어 개설을 건너뜁니다.", field("userId", userId), field("partnerUserId", partnerUserId));
        }
    }

    private boolean isBlockedEitherWay(Long userId, Long partnerUserId) {
        return blockRepository.existsByUserIdAndBlockedUserId(userId, partnerUserId)
                || blockRepository.existsByUserIdAndBlockedUserId(partnerUserId, userId);
    }

    private BalanceGameQuestion questionOf(BalanceGameRound round) {
        return balanceGameQuestionLoader.findQuestion(round.getQuestionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTIMACY_QUIZ_NOT_FOUND));
    }
}
