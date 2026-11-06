package com.nidus.twinly.invite.service;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.invite.client.AppleOfferSignature;
import com.nidus.twinly.invite.client.AppleOfferSigner;
import com.nidus.twinly.invite.config.AppStoreProperties;
import com.nidus.twinly.invite.domain.InvitePolicy;
import com.nidus.twinly.invite.domain.InviteRewardMethod;
import com.nidus.twinly.invite.domain.InviteRewardStatus;
import com.nidus.twinly.invite.domain.PromotionalSource;
import com.nidus.twinly.invite.entity.InviteRegistration;
import com.nidus.twinly.invite.entity.InviteReward;
import com.nidus.twinly.invite.repository.InviteRegistrationRepository;
import com.nidus.twinly.invite.repository.InviteRewardRepository;
import com.nidus.twinly.invite.repository.TwinCreationRepository;
import com.nidus.twinly.me.dto.result.MeInviteRewardResult;
import com.nidus.twinly.me.dto.result.MeInviteRewardRewardResult;
import com.nidus.twinly.me.dto.result.MeIssueInviteRewardAppleOfferSignatureResult;
import com.nidus.twinly.parallelrelation.repository.ParallelRelationCodeRepository;
import com.nidus.twinly.parallelrelation.service.ParallelRelationService;
import com.nidus.twinly.purchase.client.RevenueCatSubscription;
import com.nidus.twinly.purchase.entity.UserEntitlement;
import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.purchase.repository.EarlySignupGrantRepository;
import com.nidus.twinly.purchase.repository.UserEntitlementRepository;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InviteService {

    private final UserRepository userRepository;
    private final ParallelRelationCodeRepository parallelRelationCodeRepository;
    private final ParallelRelationService parallelRelationService;
    private final InviteRegistrationRepository inviteRegistrationRepository;
    private final InviteRewardRepository inviteRewardRepository;
    private final TwinCreationRepository twinCreationRepository;
    private final UserEntitlementRepository userEntitlementRepository;
    private final EarlySignupGrantRepository earlySignupGrantRepository;
    private final AppleOfferSigner appleOfferSigner;
    private final AppStoreProperties appStoreProperties;

    @Transactional
    public void registerCode(Long userId, String code) {
        User user = userRepository.findWithLockById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Long inviterId = parallelRelationCodeRepository.findByCode(code.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new BusinessException(ErrorCode.PARALLEL_RELATION_CODE_NOT_FOUND))
                .getUserId();

        if (inviterId.equals(userId)) {
            throw new BusinessException(ErrorCode.OWN_PARALLEL_RELATION_CODE);
        }

        Optional<InviteRegistration> registered = inviteRegistrationRepository.findByInviteeUserId(userId);
        if (registered.isPresent()) {
            if (registered.get().getInviterUserId().equals(inviterId)) {
                return;
            }
            throw new BusinessException(ErrorCode.INVITE_CODE_ALREADY_REGISTERED);
        }

        boolean inviterActive = userRepository.findById(inviterId)
                .filter(inviter -> !inviter.isWithdrawn())
                .isPresent();
        if (!inviterActive) {
            throw new BusinessException(ErrorCode.PARALLEL_RELATION_CODE_NOT_FOUND);
        }

        if (hasCreatedTwin(user)) {
            throw new BusinessException(ErrorCode.INVITE_CODE_NOT_ELIGIBLE);
        }

        inviteRegistrationRepository.save(InviteRegistration.register(inviterId, userId, Instant.now()));
    }

    @Transactional
    public MeInviteRewardResult inviteReward(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String code = parallelRelationService.issueCode(userId).code();
        InviteReward reward = inviteRewardRepository.findByUserId(userId).orElse(null);
        Instant now = Instant.now();

        return new MeInviteRewardResult(
                code,
                InvitePolicy.shareMessage(code),
                !inviteRegistrationRepository.existsByInviteeUserId(userId) && !hasCreatedTwin(user),
                inviteRegistrationRepository.countByInviterUserId(userId),
                inviteRegistrationRepository.countByInviterUserIdAndCompletedAtIsNotNull(userId),
                InvitePolicy.GOAL,
                reward == null ? null : toRewardResult(reward),
                promotionalSource(userId, reward, now)
        );
    }

    @Transactional
    public MeInviteRewardResult acceptAppleOffer(Long userId, String transactionId) {
        InviteReward reward = inviteRewardRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_REWARD_NOT_PENDING));

        boolean alreadyAccepted = reward.getStatus() == InviteRewardStatus.APPLE_OFFER_ACCEPTED
                && transactionId.equals(reward.getAppleTransactionId());

        if (!alreadyAccepted && inviteRewardRepository.acceptAppleOffer(
                reward.getId(),
                transactionId,
                InviteRewardStatus.APPLE_OFFER_PENDING,
                InviteRewardStatus.APPLE_OFFER_ACCEPTED,
                Instant.now()) == 0) {
            throw new BusinessException(ErrorCode.INVITE_REWARD_NOT_PENDING);
        }

        return inviteReward(userId);
    }

    public MeIssueInviteRewardAppleOfferSignatureResult issueAppleOfferSignature(Long userId) {
        InviteReward reward = inviteRewardRepository.findByUserId(userId)
                .filter(found -> found.getStatus() == InviteRewardStatus.APPLE_OFFER_PENDING)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_REWARD_NOT_PENDING));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        AppleOfferSignature signature = appleOfferSigner.sign(reward.getProductId(), user.getRevenueCatUserId(), Instant.now());

        return new MeIssueInviteRewardAppleOfferSignatureResult(
                signature.productId(),
                signature.offerId(),
                signature.keyId(),
                signature.nonce(),
                signature.timestamp(),
                signature.appAccountToken(),
                signature.signature()
        );
    }

    public Optional<String> pendingAppleOfferId(Long userId) {
        return inviteRewardRepository.findByUserId(userId)
                .filter(reward -> reward.getStatus() == InviteRewardStatus.APPLE_OFFER_PENDING)
                .map(reward -> appleOfferId());
    }

    private boolean hasCreatedTwin(User user) {
        return user.getAiChatCompletedAt() != null
                || twinCreationRepository.existsByUserId(user.getId())
                || user.getDiHash() != null && twinCreationRepository.existsByDiHash(user.getDiHash());
    }

    private MeInviteRewardRewardResult toRewardResult(InviteReward reward) {
        boolean waiting = reward.getStatus() == InviteRewardStatus.WAITING;
        boolean granted = reward.isGranted();

        return new MeInviteRewardRewardResult(
                reward.getStatus(),
                waiting && !reward.isGranting() ? reward.getWaitingReason() : null,
                granted ? reward.getMethod() : null,
                reward.getStatus() == InviteRewardStatus.APPLE_OFFER_PENDING ? appleOfferId() : null,
                granted ? reward.getEndsAt() : null
        );
    }

    private String appleOfferId() {
        return appStoreProperties.configured() ? appStoreProperties.offerId() : null;
    }

    private PromotionalSource promotionalSource(Long userId, InviteReward reward, Instant now) {
        boolean promotionalActive = userEntitlementRepository.findByUserIdAndEntitlement(userId, EntitlementReader.SIMULATION_ACCESS)
                .filter(entitlement -> RevenueCatSubscription.PROMOTIONAL.equals(entitlement.getStore()))
                .map(UserEntitlement::getExpiresAt)
                .filter(expiresAt -> expiresAt.isAfter(now))
                .isPresent();
        if (!promotionalActive) {
            return null;
        }

        boolean earlySignupActive = earlySignupGrantRepository.findByUserId(userId)
                .filter(grant -> grant.getGrantedAt() != null && grant.getExpiresAt().isAfter(now))
                .isPresent();
        if (earlySignupActive) {
            return PromotionalSource.EARLY_SIGNUP;
        }

        boolean inviteRewardActive = reward != null
                && reward.isGranted()
                && reward.getMethod() == InviteRewardMethod.PROMOTIONAL
                && !reward.getBaseExpiresAt().isAfter(now)
                && reward.getEndsAt().isAfter(now);

        return inviteRewardActive ? PromotionalSource.INVITE_REWARD : null;
    }
}
