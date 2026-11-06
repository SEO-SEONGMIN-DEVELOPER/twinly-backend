package com.nidus.twinly.invite.writer;

import com.nidus.twinly.invite.domain.InvitePolicy;
import com.nidus.twinly.invite.domain.InviteRewardMethod;
import com.nidus.twinly.invite.domain.InviteRewardStatus;
import com.nidus.twinly.invite.domain.InviteRewardWaitingReason;
import com.nidus.twinly.invite.domain.RewardDecision;
import com.nidus.twinly.invite.entity.InviteReward;
import com.nidus.twinly.invite.repository.InviteRegistrationRepository;
import com.nidus.twinly.invite.repository.InviteRewardRepository;
import com.nidus.twinly.invite.repository.TwinCreationRepository;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class InviteRewardWriter {

    private static final List<InviteRewardStatus> JUDGEABLE = List.of(InviteRewardStatus.WAITING, InviteRewardStatus.APPLE_OFFER_PENDING);

    private final InviteRewardRepository inviteRewardRepository;
    private final InviteRegistrationRepository inviteRegistrationRepository;
    private final TwinCreationRepository twinCreationRepository;
    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Long> createIfReached(Long userId, Instant now) {
        if (inviteRegistrationRepository.countByInviterUserIdAndCompletedAtIsNotNull(userId) < InvitePolicy.GOAL) {
            return Optional.empty();
        }

        Optional<InviteReward> existing = inviteRewardRepository.findByUserId(userId);
        if (existing.isPresent()) {
            return existing.map(InviteReward::getId);
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getDiHash() == null || inviteRewardRepository.existsByDiHash(user.getDiHash())) {
            return Optional.empty();
        }

        return Optional.of(inviteRewardRepository.saveAndFlush(InviteReward.create(userId, user.getDiHash(), now)).getId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean startGranting(Long rewardId, RewardDecision decision, Instant now) {
        return inviteRewardRepository.startGranting(
                rewardId,
                decision.method(),
                decision.productId(),
                decision.baseExpiresAt(),
                decision.targetExpiresAt(),
                InviteRewardStatus.WAITING,
                InviteRewardStatus.GRANTED,
                now) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markAttempted(Long rewardId, int expectedAttemptCount, Instant now) {
        return inviteRewardRepository.markAttempted(rewardId, expectedAttemptCount, InviteRewardStatus.GRANTED, now) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markGranted(Long rewardId, InviteRewardMethod method, Instant endsAt, Instant now) {
        inviteRewardRepository.markGranted(rewardId, method, endsAt, InviteRewardStatus.GRANTED, now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void hold(Long rewardId, Instant now) {
        inviteRewardRepository.hold(rewardId, now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void waitFor(Long rewardId, InviteRewardWaitingReason reason, Instant now) {
        inviteRewardRepository.changeStatus(rewardId, JUDGEABLE, InviteRewardStatus.WAITING, reason, null, now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void pendAppleOffer(Long rewardId, String productId, Instant now) {
        inviteRewardRepository.changeStatus(rewardId, JUDGEABLE, InviteRewardStatus.APPLE_OFFER_PENDING, null, productId, now);
    }

    @Transactional
    public void clearDiHashesOfWithdrawnBefore(Instant cutoff) {
        twinCreationRepository.clearDiHashOfWithdrawnBefore(cutoff);
        inviteRewardRepository.clearDiHashOfWithdrawnBefore(cutoff);
    }
}
