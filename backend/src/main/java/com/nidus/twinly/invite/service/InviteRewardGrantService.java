package com.nidus.twinly.invite.service;

import com.nidus.twinly.common.logging.ErrorLog;
import com.nidus.twinly.common.logging.InfoLog;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.invite.client.AppStoreServerClient;
import com.nidus.twinly.invite.client.AppStoreTransaction;
import com.nidus.twinly.invite.config.AppStoreProperties;
import com.nidus.twinly.invite.domain.GrantCheck;
import com.nidus.twinly.invite.domain.InvitePolicy;
import com.nidus.twinly.invite.domain.InviteRewardJudge;
import com.nidus.twinly.invite.domain.InviteRewardMethod;
import com.nidus.twinly.invite.domain.InviteRewardStatus;
import com.nidus.twinly.invite.domain.RewardDecision;
import com.nidus.twinly.invite.entity.InviteReward;
import com.nidus.twinly.invite.event.InviteCompletedEvent;
import com.nidus.twinly.invite.repository.InviteRegistrationRepository;
import com.nidus.twinly.invite.repository.InviteRewardRepository;
import com.nidus.twinly.invite.writer.InviteRewardWriter;
import com.nidus.twinly.purchase.client.RevenueCatClient;
import com.nidus.twinly.purchase.client.RevenueCatSubscriptions;
import com.nidus.twinly.purchase.event.RevenueCatWebhookSyncedEvent;
import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.Comparator;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Service
@RequiredArgsConstructor
public class InviteRewardGrantService {

    private final InviteRewardRepository inviteRewardRepository;
    private final InviteRegistrationRepository inviteRegistrationRepository;
    private final InviteRewardWriter inviteRewardWriter;
    private final UserRepository userRepository;
    private final RevenueCatClient revenueCatClient;
    private final AppStoreServerClient appStoreServerClient;
    private final AppStoreProperties appStoreProperties;

    @Async("purchaseSyncTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInviteCompleted(InviteCompletedEvent event) {
        createAndProcess(event.inviterUserId());
    }

    @EventListener
    public void onRevenueCatWebhookSynced(RevenueCatWebhookSyncedEvent event) {
        try {
            inviteRewardRepository.findByUserId(event.userId())
                    .filter(reward -> !reward.isGranted() && !reward.isHeld())
                    .ifPresent(reward -> processQuietly(reward.getId()));
        } catch (RuntimeException e) {
            ErrorLog.warn(log, ErrorCode.INTERNAL_ERROR.name(), String.valueOf(event.userId()), e)
                    .log("웹훅 뒤 초대 보상 재판정에 실패했습니다. 주기 작업이 다시 시도합니다.");
        }
    }

    public void processPending() {
        inviteRegistrationRepository.findInviterIdsReachedWithoutReward(InvitePolicy.GOAL).forEach(this::createAndProcess);
        inviteRewardRepository.findIdsInProgress(InviteRewardStatus.GRANTED).forEach(this::processQuietly);
    }

    public void process(Long rewardId) {
        InviteReward reward = inviteRewardRepository.findById(rewardId).orElse(null);
        if (reward == null || reward.isGranted() || reward.isHeld()) {
            return;
        }

        User user = userRepository.findById(reward.getUserId()).orElse(null);
        if (user == null || user.isDeleted()) {
            return;
        }

        String appUserId = user.getRevenueCatUserId().toString();
        Instant now = Instant.now();

        if (reward.isGranting()) {
            continueGranting(reward, appUserId, now);
            return;
        }

        if (reward.getStatus() == InviteRewardStatus.APPLE_OFFER_ACCEPTED) {
            AppleOfferCheck appleOffer = checkAppleOffer(reward);
            if (!appleOffer.checked()) {
                return;
            }
            if (appleOffer.started()) {
                inviteRewardWriter.markGranted(rewardId, InviteRewardMethod.APPLE_OFFER, appleOffer.endsAt(), now);
                InfoLog.log(log, "애플 오퍼로 초대 보상이 시작됐습니다.", field("userId", reward.getUserId()), field("endsAt", appleOffer.endsAt()));
                return;
            }
        }

        RewardDecision decision = InviteRewardJudge.judge(subscriptions(appUserId), now);

        if (decision.grantsNow()) {
            if (inviteRewardWriter.startGranting(rewardId, decision, now)) {
                inviteRewardRepository.findById(rewardId).ifPresent(granting -> continueGranting(granting, appUserId, now));
            }
            return;
        }

        if (reward.getStatus() == InviteRewardStatus.APPLE_OFFER_ACCEPTED) {
            return;
        }

        if (decision.kind() == RewardDecision.Kind.APPLE_OFFER) {
            inviteRewardWriter.pendAppleOffer(rewardId, decision.productId(), now);
        } else {
            inviteRewardWriter.waitFor(rewardId, decision.waitingReason(), now);
        }
    }

    private void createAndProcess(Long userId) {
        try {
            inviteRewardWriter.createIfReached(userId, Instant.now()).ifPresent(this::processQuietly);
        } catch (DataIntegrityViolationException e) {
            InfoLog.log(log, "같은 사람의 초대 보상이 이미 있어 새로 만들지 않습니다.", field("userId", userId));
        } catch (RuntimeException e) {
            ErrorLog.warn(log, ErrorCode.INTERNAL_ERROR.name(), String.valueOf(userId), e)
                    .log("초대 보상 생성에 실패했습니다. 주기 작업이 다시 시도합니다.");
        }
    }

    private void processQuietly(Long rewardId) {
        try {
            process(rewardId);
        } catch (RuntimeException e) {
            ErrorLog.warn(log, ErrorCode.REVENUE_CAT_GRANT_FAILED.name(), String.valueOf(rewardId), e)
                    .log("초대 보상 처리에 실패했습니다. 주기 작업이 다시 시도합니다.");
        }
    }

    private void continueGranting(InviteReward reward, String appUserId, Instant now) {
        if (reward.isAttempted()) {
            GrantCheck check = GrantCheck.of(
                    reward.getMethod(),
                    reward.getProductId(),
                    reward.getBaseExpiresAt(),
                    reward.getTargetExpiresAt(),
                    subscriptions(appUserId));

            if (check == GrantCheck.APPLIED) {
                markGranted(reward, now);
                return;
            }
            if (check == GrantCheck.UNKNOWN) {
                inviteRewardWriter.hold(reward.getId(), now);
                ErrorLog.error(log, ErrorCode.REVENUE_CAT_GRANT_FAILED.name(), String.valueOf(reward.getUserId()), null)
                        .log("초대 보상 적용 여부를 판단할 수 없어 보류했습니다. 직접 확인이 필요합니다. rewardId={}", reward.getId());
                return;
            }
        }

        if (!inviteRewardWriter.markAttempted(reward.getId(), reward.getAttemptCount(), now)) {
            return;
        }

        switch (reward.getMethod()) {
            case PROMOTIONAL -> revenueCatClient.grantPromotional(appUserId, EntitlementReader.SIMULATION_ACCESS, reward.getTargetExpiresAt());
            case PLAY_DEFER -> revenueCatClient.defer(appUserId, reward.getProductId(), reward.getTargetExpiresAt());
            case APPLE_OFFER -> throw new IllegalStateException("애플 오퍼는 서버가 직접 지급하지 않습니다. rewardId=" + reward.getId());
        }

        markGranted(reward, Instant.now());
    }

    private void markGranted(InviteReward reward, Instant now) {
        inviteRewardWriter.markGranted(reward.getId(), reward.getMethod(), reward.getTargetExpiresAt(), now);
        InfoLog.log(log, "초대 보상을 지급했습니다.", field("userId", reward.getUserId()), field("method", reward.getMethod()), field("endsAt", reward.getTargetExpiresAt()));
    }

    private AppleOfferCheck checkAppleOffer(InviteReward reward) {
        if (!appStoreProperties.configured()) {
            return AppleOfferCheck.UNCHECKED;
        }

        try {
            return new AppleOfferCheck(true, appStoreServerClient.latestTransactions(reward.getAppleTransactionId()).stream()
                    .filter(transaction -> transaction.appliedOffer(appStoreProperties.offerId()) && transaction.expiresAt() != null)
                    .map(AppStoreTransaction::expiresAt)
                    .max(Comparator.naturalOrder())
                    .orElse(null));
        } catch (RuntimeException e) {
            ErrorLog.warn(log, ErrorCode.APP_STORE_REQUEST_FAILED.name(), String.valueOf(reward.getUserId()), e)
                    .log("애플 오퍼 시작 여부를 확인하지 못했습니다. 주기 작업이 다시 확인합니다.");
            return AppleOfferCheck.UNCHECKED;
        }
    }

    private RevenueCatSubscriptions subscriptions(String appUserId) {
        return revenueCatClient.subscriptions(appUserId, EntitlementReader.SIMULATION_ACCESS);
    }

    private record AppleOfferCheck(
            boolean checked,
            Instant endsAt
    ) {

        private static final AppleOfferCheck UNCHECKED = new AppleOfferCheck(false, null);

        boolean started() {
            return endsAt != null;
        }
    }
}
