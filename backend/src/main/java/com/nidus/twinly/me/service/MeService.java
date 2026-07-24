package com.nidus.twinly.me.service;

import com.nidus.twinly.activity.domain.QuestionType;
import com.nidus.twinly.activity.entity.Question;
import com.nidus.twinly.activity.repository.QuestionRepository;
import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.common.crypto.BlindIndexHasher;
import com.nidus.twinly.common.photo.PhotoPosInfo;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.common.presign.PhotoCommitService;
import com.nidus.twinly.common.presign.PhotoPresignResult;
import com.nidus.twinly.common.presign.PresignService;
import com.nidus.twinly.legal.entity.Agreement;
import com.nidus.twinly.legal.entity.Policy;
import com.nidus.twinly.legal.entity.PolicyName;
import com.nidus.twinly.legal.repository.AgreementRepository;
import com.nidus.twinly.legal.repository.PolicyNameRepository;
import com.nidus.twinly.legal.repository.PolicyRepository;
import com.nidus.twinly.legal.service.PolicyCatalog;
import com.nidus.twinly.legal.service.PolicyCatalog.PolicyKey;
import com.nidus.twinly.me.domain.HesitationDuration;
import com.nidus.twinly.me.domain.HesitationStatus;
import com.nidus.twinly.me.dto.command.MeAppNotificationsReadAllCommand;
import com.nidus.twinly.me.dto.command.MeChangeProfileVisibilityCommand;
import com.nidus.twinly.me.dto.command.MeChangePushNotificationsCommand;
import com.nidus.twinly.me.dto.command.MeGrantConsentsCommand;
import com.nidus.twinly.me.dto.command.MeHesitationsAnswerCommand;
import com.nidus.twinly.me.dto.command.MeProfileCommand;
import com.nidus.twinly.me.dto.command.MeProfilePhotoCommitCommand;
import com.nidus.twinly.me.dto.command.MeProfilePhotoPresignCommand;
import com.nidus.twinly.me.dto.command.MeRevokeConsentsCommand;
import com.nidus.twinly.me.dto.result.MeAppNotificationsFeedsChatTargetResult;
import com.nidus.twinly.me.dto.result.MeAppNotificationsFeedsItemResult;
import com.nidus.twinly.me.dto.result.MeAppNotificationsFeedsProfileTargetResult;
import com.nidus.twinly.me.dto.result.MeAppNotificationsFeedsResult;
import com.nidus.twinly.me.dto.result.MeAppNotificationsFeedsTargetResult;
import com.nidus.twinly.me.dto.result.MeAppNotificationsUnreadCountResult;
import com.nidus.twinly.me.dto.result.MeConsentsItemResult;
import com.nidus.twinly.me.dto.result.MeConsentsResult;
import com.nidus.twinly.me.dto.result.MeHesitationsResult;
import com.nidus.twinly.me.dto.result.MePushNotificationsResult;
import com.nidus.twinly.me.dto.result.MePushNotificationsSettingsResult;
import com.nidus.twinly.me.dto.result.MeProfileEditViewResult;
import com.nidus.twinly.me.dto.result.MeProfilePhotoCommitResult;
import com.nidus.twinly.me.dto.result.MeProfilePhotoPresignResult;
import com.nidus.twinly.me.dto.result.MeProfileVisibilityResult;
import com.nidus.twinly.me.dto.result.MeStatusReportResult;
import com.nidus.twinly.me.dto.result.MeStatusResult;
import com.nidus.twinly.me.dto.result.MeStatusWithdrawalResult;
import com.nidus.twinly.me.dto.result.MeWithdrawResult;
import com.nidus.twinly.notification.domain.AppNotificationFeedType;
import com.nidus.twinly.notification.domain.NotificationChannel;
import com.nidus.twinly.notification.domain.NotificationType;
import com.nidus.twinly.notification.entity.AppNotificationFeed;
import com.nidus.twinly.notification.entity.NotificationSetting;
import com.nidus.twinly.notification.repository.AppNotificationFeedRepository;
import com.nidus.twinly.notification.repository.NotificationSettingRepository;
import com.nidus.twinly.report.domain.ReportStatus;
import com.nidus.twinly.report.entity.Report;
import com.nidus.twinly.report.repository.ReportRepository;
import com.nidus.twinly.user.domain.DisclosureField;
import com.nidus.twinly.user.entity.DisclosureAgreement;
import com.nidus.twinly.user.entity.Photo;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.DisclosureAgreementRepository;
import com.nidus.twinly.user.repository.PhotoRepository;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/*
 * [멘토링 피드백 반영 완료]
 * JPA에서 업데이트된 정보가 분실되는 상황 발생 -> 그 컬럼만 바꾸게 SQL 짜기
 */

@Service
@RequiredArgsConstructor
public class MeService {

    private static final Duration WITHDRAWAL_RECOVERABLE_PERIOD = Duration.ofDays(15);
    private static final int DEFAULT_APP_NOTIFICATIONS_LIMIT = 20;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PresignService presignService;
    private final PhotoCommitService photoCommitService;
    private final CloudFrontService cloudFrontService;

    private final BlindIndexHasher blindIndexHasher;

    private final PhotoRepository photoRepository;
    private final UserRepository userRepository;
    private final PolicyNameRepository policyNameRepository;
    private final PolicyRepository policyRepository;
    private final AgreementRepository agreementRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final DisclosureAgreementRepository disclosureAgreementRepository;
    private final AppNotificationFeedRepository appNotificationFeedRepository;
    private final ReportRepository reportRepository;
    private final QuestionRepository questionRepository;

    private final PolicyCatalog policyCatalog;

    public MeProfilePhotoPresignResult profilePhotoPresign(Long userId, MeProfilePhotoPresignCommand command) {
        PhotoPresignResult presign = presignService.presignPhoto(userId, command.contentType(), PhotoType.PROFILE);

        return new MeProfilePhotoPresignResult(presign.uploadUrl(), presign.key(), presign.method(), presign.requiredHeaders(), presign.maxBytes(), presign.expiresAt());
    }

    public MeProfilePhotoCommitResult profilePhotoCommit(Long userId, MeProfilePhotoCommitCommand command) {
        String photoUrl = photoCommitService.commitProfilePhoto(userId, command.key());

        PhotoPosInfo position = command.position();
        photoRepository.findByUserIdAndType(userId, PhotoType.PROFILE)
                .ifPresentOrElse(
                        photo -> photo.changePhoto(command.key(),
                                position.startPos().x(), position.startPos().y(), position.width(), position.height()),
                        () -> photoRepository.save(Photo.create(userId, PhotoType.PROFILE, command.key(),
                                position.startPos().x(), position.startPos().y(), position.width(), position.height(), Instant.now()))
                );

        return new MeProfilePhotoCommitResult(photoUrl, position);
    }

    @Transactional
    public MeWithdrawResult withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 유저입니다."));

        if (user.getWithdrawalRequestedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 탈퇴 신청이 된 상태입니다.");
        }

        user.requestWithdrawal(WITHDRAWAL_RECOVERABLE_PERIOD);

        return new MeWithdrawResult(user.getWithdrawalScheduledAt());
    }

    public MeProfileEditViewResult profileEditView(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 유저입니다."));

        String profilePhotoUrl = photoRepository.findByUserIdAndType(userId, PhotoType.PROFILE)
                .map(photo -> cloudFrontService.getSignedUrl(photo.getKey()))
                .orElse(null);

        return new MeProfileEditViewResult(
                user.getId(),
                user.getFamilyName(),
                user.getGivenName(),
                user.getAffiliation(),
                user.getAffiliationNumber(),
                user.getBirthDate(),
                profilePhotoUrl
        );
    }

    @Transactional
    public void profile(Long userId, MeProfileCommand command) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 유저입니다."));

        user.changeAffiliation(command.affiliation(), blindIndexHasher.hash(command.affiliation()));
    }

    @Transactional
    public void restore(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 유저입니다."));

        if (user.getWithdrawalRequestedAt() == null) {
            return;
        }

        if (user.getWithdrawalRequestedAt().plus(WITHDRAWAL_RECOVERABLE_PERIOD).isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "복구 가능 기간이 지났습니다.");
        }

        user.cancelWithdrawal();
    }

    public MeConsentsResult consents(Long userId) {
        List<PolicyName> policyNames = policyNameRepository.findAllByIsDeprecatedFalse();
        List<Long> policyNameIds = policyNames.stream().map(PolicyName::getId).toList();

        Instant now = Instant.now();
        Map<Long, Policy> currentByPolicyNameId = policyRepository.findAllByPolicyNameIdIn(policyNameIds).stream()
                .filter(policy -> policy.getEffectiveAt() != null && !policy.getEffectiveAt().isAfter(now))
                .collect(Collectors.toMap(
                        Policy::getPolicyNameId,
                        Function.identity(),
                        (a, b) -> a.getEffectiveAt().isAfter(b.getEffectiveAt()) ? a : b));

        Map<Long, Agreement> agreementByPolicyId = agreementRepository.findAllByUserIdAndRevokedAtIsNull(userId).stream()
                .collect(Collectors.toMap(
                        Agreement::getPolicyId,
                        Function.identity(),
                        (a, b) -> a.getAgreedAt().isAfter(b.getAgreedAt()) ? a : b));

        List<MeConsentsItemResult> consents = policyNames.stream()
                .map(policyName -> {
                    Policy current = currentByPolicyNameId.get(policyName.getId());
                    Agreement agreement = current != null ? agreementByPolicyId.get(current.getId()) : null;
                    return new MeConsentsItemResult(
                            policyName.getId(),
                            policyName.getName(),
                            current != null ? current.getVersion() : null,
                            current != null ? current.getUrl() : null,
                            current != null ? current.getIsRequired() : null,
                            agreement != null,
                            agreement != null ? agreement.getAgreedAt() : null);
                })
                .toList();

        return new MeConsentsResult(consents);
    }

    @Transactional
    public void grantConsents(Long userId, MeGrantConsentsCommand command) {
        List<Long> policyNameIds = command.grants().stream().map(grant -> grant.policyId()).toList();

        Map<PolicyKey, Policy> policyByKey = policyCatalog.loadByKey(policyNameIds);

        Set<Long> alreadyAgreedPolicyIds = agreementRepository.findAllByUserIdAndRevokedAtIsNull(userId).stream()
                .map(Agreement::getPolicyId)
                .collect(Collectors.toSet());

        Instant now = Instant.now();
        List<Agreement> agreements = command.grants().stream()
                .map(grant -> {
                    Policy policy = policyByKey.get(new PolicyKey(grant.policyId(), grant.version()));
                    if (policy == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 정책 또는 버전입니다.");
                    }
                    return policy;
                })
                .filter(policy -> !alreadyAgreedPolicyIds.contains(policy.getId()))
                .map(policy -> Agreement.create(userId, policy.getId(), now))
                .toList();

        agreementRepository.saveAll(agreements);
    }

    @Transactional
    public void revokeConsents(Long userId, MeRevokeConsentsCommand command) {
        List<Long> policyNameIds = command.grants().stream().map(grant -> grant.policyId()).toList();

        Map<PolicyKey, Policy> policyByKey = policyCatalog.loadByKey(policyNameIds);

        List<Policy> policies = command.grants().stream()
                .map(grant -> policyByKey.get(new PolicyKey(grant.policyId(), grant.version())))
                .filter(policy -> policy != null)
                .toList();

        if (policies.stream().anyMatch(policy -> Boolean.TRUE.equals(policy.getIsRequired()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "필수 정책은 철회할 수 없습니다.");
        }

        List<Long> policyIdsToRevoke = policies.stream().map(Policy::getId).toList();
        if (!policyIdsToRevoke.isEmpty()) {
            agreementRepository.revokeWithPreviousVersionsByUserIdAndPolicyIdIn(userId, policyIdsToRevoke);
        }
    }

    public MePushNotificationsResult pushNotifications(Long userId) {
        Map<NotificationType, Boolean> enabledByType = notificationSettingRepository
                .findAllByUserIdAndChannel(userId, NotificationChannel.PUSH).stream()
                .collect(Collectors.toMap(NotificationSetting::getType, NotificationSetting::getEnabled));

        MePushNotificationsSettingsResult settings = new MePushNotificationsSettingsResult(
                enabledByType.getOrDefault(NotificationType.EVENT, true),
                enabledByType.getOrDefault(NotificationType.CHAT, true),
                enabledByType.getOrDefault(NotificationType.MARKETING, true));

        return new MePushNotificationsResult(settings);
    }

    @Transactional
    public void changePushNotifications(Long userId, NotificationType type, MeChangePushNotificationsCommand command) {
        notificationSettingRepository.findByUserIdAndChannelAndType(userId, NotificationChannel.PUSH, type)
                .ifPresentOrElse(
                        notificationSetting -> notificationSetting.changeEnabled(command.isEnabled()),
                        () -> notificationSettingRepository.save(
                                NotificationSetting.create(userId, NotificationChannel.PUSH, type, command.isEnabled()))
                );
    }

    public MeProfileVisibilityResult profileVisibility(Long userId) {
        Set<DisclosureField> disclosedFields = disclosureAgreementRepository.findAllByUserId(userId).stream()
                .map(DisclosureAgreement::getField)
                .collect(Collectors.toSet());

        return new MeProfileVisibilityResult(
                disclosedFields.contains(DisclosureField.AFFILIATION),
                disclosedFields.contains(DisclosureField.AFFILIATION_NUMBER));
    }

    @Transactional
    public void changeProfileVisibility(Long userId, DisclosureField type, MeChangeProfileVisibilityCommand command) {
        if (command.isVisible()) {
            if (!disclosureAgreementRepository.existsByUserIdAndField(userId, type)) {
                disclosureAgreementRepository.save(DisclosureAgreement.create(userId, type));
            }
            return;
        }

        disclosureAgreementRepository.deleteByUserIdAndField(userId, type);
    }

    public MeAppNotificationsFeedsResult appNotificationsFeeds(Long userId, Boolean unreadOnly, AppNotificationFeedType type, Integer limit) {
        int effectiveLimit = (limit != null && limit > 0) ? limit : DEFAULT_APP_NOTIFICATIONS_LIMIT;

        List<MeAppNotificationsFeedsItemResult> appNotificationFeeds = appNotificationFeedRepository
                .findAllByUserIdAndFilter(userId, Boolean.TRUE.equals(unreadOnly), type != null ? type.name() : null, effectiveLimit).stream()
                .map(feed -> new MeAppNotificationsFeedsItemResult(
                        feed.getId(),
                        feed.getType(),
                        feed.getTitle(),
                        feed.getBody(),
                        toTargetResult(feed),
                        feed.getReadAt() != null,
                        feed.getCreatedAt()
                ))
                .toList();

        return new MeAppNotificationsFeedsResult(appNotificationFeedRepository.countByUserIdAndReadAtIsNull(userId), appNotificationFeeds);
    }
    
    public MeAppNotificationsUnreadCountResult appNotificationsUnreadCount(Long userId) {
        return new MeAppNotificationsUnreadCountResult(appNotificationFeedRepository.countByUserIdAndReadAtIsNull(userId));
    }

    private MeAppNotificationsFeedsTargetResult toTargetResult(AppNotificationFeed feed) {
        return switch (feed.getTargetKind()) {
            case PROFILE -> new MeAppNotificationsFeedsProfileTargetResult("profile", feed.getTargetUserId());
            case CHAT -> new MeAppNotificationsFeedsChatTargetResult("chat", feed.getTargetChatRoomId());
        };
    }

    @Transactional
    public void appNotificationsRead(Long userId, Long appNotificationId) {
        AppNotificationFeed feed = appNotificationFeedRepository.findByIdAndUserId(appNotificationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 알림입니다."));

        if (feed.getReadAt() != null) {
            return;
        }

        feed.markRead();
    }

    @Transactional
    public void appNotificationsReadAll(Long userId, MeAppNotificationsReadAllCommand command) {
        appNotificationFeedRepository.markAllReadByUserIdAndIdLessThanEqual(userId, command.lastAppNotificationId());
    }

    public MeStatusResult status(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 유저입니다."));

        MeStatusWithdrawalResult withdrawal = new MeStatusWithdrawalResult(
                user.getWithdrawalRequestedAt() != null,
                user.getWithdrawalScheduledAt()
        );

        List<Report> reports = reportRepository.findAllByReportedUserIdAndStatusNot(userId, ReportStatus.REJECTED);
        List<String> reasons = reports.stream()
                .map(report -> report.getReason().name())
                .distinct()
                .toList();

        return new MeStatusResult(
                withdrawal,
                new MeStatusReportResult(!reports.isEmpty(), reasons)
        );
    }

    public MeHesitationsResult hesitations(Long userId, HesitationDuration duration, HesitationStatus status) {
        LocalDate today = LocalDate.now(KST);

        List<Question> candidates = switch (duration) {
            case TODAY -> questionRepository.findAllByUserIdAndTypeAndIsSkippedFalseAndDate(userId, QuestionType.PERSONA, today);
            case ALL -> questionRepository.findAllByUserIdAndTypeAndIsSkippedFalse(userId, QuestionType.PERSONA);
        };

        Predicate<Question> statusFilter = switch (status) {
            case ANSWERED -> question -> question.getAnsweredAt() != null;
            case UNANSWERED -> question -> question.getAnsweredAt() == null;
            case ALL -> question -> true;
        };

        List<Long> hesitationIds = candidates.stream()
                .filter(statusFilter)
                .map(Question::getId)
                .toList();

        return new MeHesitationsResult(today, hesitationIds);
    }

    @Transactional
    public void hesitationsAnswer(Long userId, Long hesitationId, MeHesitationsAnswerCommand command) {
        Question question = questionRepository.findById(hesitationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 망설임입니다."));

        if (!question.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인의 망설임이 아닙니다.");
        }

        if (question.getAnsweredAt() != null || Boolean.TRUE.equals(question.getIsSkipped())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 망설임입니다.");
        }

        if (Boolean.TRUE.equals(command.skipped())) {
            question.skip();
            return;
        }

        if (command.answer() == null || command.answer().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "답변 내용이 없습니다.");
        }

        if (!question.getOptions().contains(command.answer())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "선택지에 없는 답변입니다.");
        }

        question.answer(command.answer());
    }
}
