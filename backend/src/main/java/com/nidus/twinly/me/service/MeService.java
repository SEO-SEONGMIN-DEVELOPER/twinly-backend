package com.nidus.twinly.me.service;

import com.nidus.twinly.activity.domain.QuestionType;
import com.nidus.twinly.activity.entity.Question;
import com.nidus.twinly.activity.repository.QuestionRepository;
import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.common.crypto.BlindIndexHasher;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.photo.PhotoPosInfo;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.common.photo.ProfilePhotoInfo;
import com.nidus.twinly.common.photo.ProfileThumbnailService;
import com.nidus.twinly.common.presign.PhotoCommitResult;
import com.nidus.twinly.common.presign.PhotoCommitService;
import com.nidus.twinly.common.presign.PhotoPresignResult;
import com.nidus.twinly.common.presign.PresignService;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.legal.entity.Agreement;
import com.nidus.twinly.legal.repository.PolicyRepository.PolicySummary;
import com.nidus.twinly.legal.entity.PolicyName;
import com.nidus.twinly.legal.repository.AgreementRepository;
import com.nidus.twinly.legal.repository.PolicyNameRepository;
import com.nidus.twinly.legal.service.PolicyCatalog;
import com.nidus.twinly.legal.service.PolicyCatalog.PolicyKey;
import com.nidus.twinly.me.domain.HesitationDuration;
import com.nidus.twinly.me.domain.HesitationStatus;
import com.nidus.twinly.me.dto.command.MeAppNotificationsReadAllCommand;
import com.nidus.twinly.me.dto.command.MeChangeProfileVisibilitySettingCommand;
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
import com.nidus.twinly.me.dto.result.MeProfileResult;
import com.nidus.twinly.me.dto.result.MeProfilePhotoCommitResult;
import com.nidus.twinly.me.dto.result.MeProfilePhotoPresignResult;
import com.nidus.twinly.me.dto.result.MeProfileVisibilitySettingsResult;
import com.nidus.twinly.me.dto.result.MePurchasesResult;
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
import com.nidus.twinly.people.repository.EncounterRepository;
import com.nidus.twinly.relationship.domain.RelationshipType;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.report.domain.ReportStatus;
import com.nidus.twinly.report.entity.Report;
import com.nidus.twinly.report.repository.ReportRepository;
import com.nidus.twinly.subscription.entity.UserEntitlement;
import com.nidus.twinly.subscription.repository.UserEntitlementRepository;
import com.nidus.twinly.user.domain.DisclosureField;
import com.nidus.twinly.user.entity.DisclosureAgreement;
import com.nidus.twinly.user.entity.PersonaElement;
import com.nidus.twinly.user.entity.Photo;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.DisclosureAgreementRepository;
import com.nidus.twinly.user.repository.PersonaElementRepository;
import com.nidus.twinly.user.repository.PhotoRepository;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeService {

    private static final Duration WITHDRAWAL_RECOVERABLE_PERIOD = Duration.ofDays(15);
    private static final int DEFAULT_APP_NOTIFICATIONS_LIMIT = 20;
    private static final int PERSONA_SUMMARY_SIZE = 3;
    private static final String PERSONA_SUMMARY_DELIMITER = ", ";
    private static final String PERSONA_SUMMARY_SUFFIX = "...";
    private static final List<PersonaDimension> PERSONA_SUMMARY_DIMENSIONS = List.of(
            PersonaDimension.OPENNESS,
            PersonaDimension.CONSCIENTIOUSNESS,
            PersonaDimension.EXTRAVERSION,
            PersonaDimension.AGREEABLENESS,
            PersonaDimension.NEUROTICISM,
            PersonaDimension.LIFE_STYLE,
            PersonaDimension.CONFLICT_STYLE,
            PersonaDimension.COMMUNICATION_STYLE);

    private final PresignService presignService;
    private final PhotoCommitService photoCommitService;
    private final ProfileThumbnailService profileThumbnailService;
    private final CloudFrontService cloudFrontService;

    private final BlindIndexHasher blindIndexHasher;

    private final PhotoRepository photoRepository;
    private final UserRepository userRepository;
    private final PolicyNameRepository policyNameRepository;
    private final AgreementRepository agreementRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final DisclosureAgreementRepository disclosureAgreementRepository;
    private final AppNotificationFeedRepository appNotificationFeedRepository;
    private final ReportRepository reportRepository;
    private final QuestionRepository questionRepository;
    private final PersonaElementRepository personaElementRepository;
    private final EncounterRepository encounterRepository;
    private final RelationshipRepository relationshipRepository;
    private final UserEntitlementRepository userEntitlementRepository;

    private final PolicyCatalog policyCatalog;

    public MeProfilePhotoPresignResult profilePhotoPresign(Long userId, MeProfilePhotoPresignCommand command) {
        PhotoPresignResult presign = presignService.presignPhoto(userId, command.contentType(), PhotoType.PROFILE);

        return new MeProfilePhotoPresignResult(presign.uploadUrl(), presign.key(), presign.method(), presign.requiredHeaders(), presign.maxBytes(), presign.expiresAt());
    }

    @Transactional
    public MeProfilePhotoCommitResult profilePhotoCommit(Long userId, MeProfilePhotoCommitCommand command) {
        PhotoCommitResult commit = photoCommitService.commitProfilePhoto(userId, command.key());

        PhotoPosInfo position = command.position();
        String thumbnailKey = profileThumbnailService.generate(command.key(), position, commit.sourceBytes());

        photoRepository.findByUserIdAndType(userId, PhotoType.PROFILE)
                .ifPresentOrElse(
                        photo -> {
                            photo.changePhoto(command.key(),
                                    position.startPos().x(), position.startPos().y(), position.width(), position.height());
                            photo.changeThumbnailKey(thumbnailKey);
                        },
                        () -> {
                            Photo photo = Photo.create(userId, PhotoType.PROFILE, command.key(),
                                    position.startPos().x(), position.startPos().y(), position.width(), position.height(), Instant.now());
                            photo.changeThumbnailKey(thumbnailKey);
                            photoRepository.save(photo);
                        }
                );

        return new MeProfilePhotoCommitResult(commit.photoUrl(), position);
    }

    @Transactional
    public MeWithdrawResult withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getWithdrawalRequestedAt() != null) {
            return new MeWithdrawResult(user.getWithdrawalScheduledAt());
        }

        user.requestWithdrawal(WITHDRAWAL_RECOVERABLE_PERIOD);

        return new MeWithdrawResult(user.getWithdrawalScheduledAt());
    }

    public MeProfileEditViewResult profileEditView(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        ProfilePhotoInfo profilePhoto = photoRepository.findByUserIdAndType(userId, PhotoType.PROFILE)
                .map(photo -> new ProfilePhotoInfo(photo.getKey(), cloudFrontService.getSignedUrl(photo.getKey()), photo.position()))
                .orElse(null);

        return new MeProfileEditViewResult(
                user.getId(),
                user.getFamilyName(),
                user.getGivenName(),
                user.getAffiliation(),
                user.getAffiliationNumber(),
                user.getBirthDate(),
                profilePhoto
        );
    }

    @Transactional
    public void profile(Long userId, MeProfileCommand command) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        user.changeAffiliation(command.affiliation(), blindIndexHasher.hash(command.affiliation()));
    }

    @Transactional
    public void restore(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getWithdrawalRequestedAt() == null) {
            return;
        }

        if (user.getWithdrawalRequestedAt().plus(WITHDRAWAL_RECOVERABLE_PERIOD).isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.WITHDRAWAL_RECOVERY_EXPIRED);
        }

        user.cancelWithdrawal();
    }

    public MeConsentsResult consents(Long userId) {
        List<PolicyName> policyNames = policyNameRepository.findAllByIsDeprecatedFalseOrderByIdAsc();
        List<Long> policyNameIds = policyNames.stream().map(PolicyName::getId).toList();

        Map<Long, PolicySummary> currentByPolicyNameId = policyCatalog.loadLatestByPolicyNameId(policyNameIds);

        Map<Long, Agreement> agreementByPolicyId = agreementRepository.findAllByUserIdAndRevokedAtIsNull(userId).stream()
                .collect(Collectors.toMap(
                        Agreement::getPolicyId,
                        Function.identity(),
                        (a, b) -> a.getAgreedAt().isAfter(b.getAgreedAt()) ? a : b));

        List<MeConsentsItemResult> consents = policyNames.stream()
                .map(policyName -> {
                    PolicySummary current = currentByPolicyNameId.get(policyName.getId());
                    Agreement agreement = current != null ? agreementByPolicyId.get(current.getId()) : null;
                    return new MeConsentsItemResult(
                            policyName.getIdentifier(),
                            policyName.getName(),
                            current != null ? current.getVersion() : null,
                            current != null ? cloudFrontService.getPublicUrl(current.getKey()) : null,
                            policyName.getRequiresAgreement(),
                            current != null ? current.getIsRequired() : null,
                            agreement != null,
                            agreement != null ? agreement.getAgreedAt() : null);
                })
                .toList();

        return new MeConsentsResult(consents);
    }

    @Transactional
    public void grantConsents(Long userId, MeGrantConsentsCommand command) {
        List<String> policyNameIdentifiers = command.grants().stream().map(grant -> grant.policyId()).toList();

        Map<PolicyKey, PolicySummary> policyByKey = policyCatalog.loadByKey(policyNameIdentifiers);

        Set<Long> alreadyAgreedPolicyIds = agreementRepository.findAllByUserIdAndRevokedAtIsNull(userId).stream()
                .map(Agreement::getPolicyId)
                .collect(Collectors.toSet());

        Instant now = Instant.now();
        List<Agreement> agreements = command.grants().stream()
                .map(grant -> {
                    PolicySummary policy = policyByKey.get(new PolicyKey(grant.policyId(), grant.version()));
                    if (policy == null) {
                        throw new BusinessException(ErrorCode.POLICY_NOT_FOUND);
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
        List<String> policyNameIdentifiers = command.grants().stream().map(grant -> grant.policyId()).toList();

        Map<PolicyKey, PolicySummary> policyByKey = policyCatalog.loadByKey(policyNameIdentifiers);

        List<PolicySummary> policies = command.grants().stream()
                .map(grant -> {
                    PolicySummary policy = policyByKey.get(new PolicyKey(grant.policyId(), grant.version()));
                    if (policy == null) {
                        throw new BusinessException(ErrorCode.POLICY_NOT_FOUND);
                    }
                    return policy;
                })
                .toList();

        if (policies.stream().anyMatch(policy -> Boolean.TRUE.equals(policy.getIsRequired()))) {
            throw new BusinessException(ErrorCode.REQUIRED_POLICY_REVOKE_DENIED);
        }

        List<Long> policyIdsToRevoke = policies.stream().map(PolicySummary::getId).toList();
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
        notificationSettingRepository.upsertEnabled(
                userId, NotificationChannel.PUSH.name(), type.name(), command.isEnabled());
    }

    public MeProfileVisibilitySettingsResult profileVisibilitySettings(Long userId) {
        Set<DisclosureField> disclosedFields = disclosureAgreementRepository.findAllByUserId(userId).stream()
                .map(DisclosureAgreement::getField)
                .collect(Collectors.toSet());

        return new MeProfileVisibilitySettingsResult(
                disclosedFields.contains(DisclosureField.AFFILIATION),
                disclosedFields.contains(DisclosureField.AFFILIATION_NUMBER));
    }

    @Transactional
    public void changeProfileVisibilitySetting(Long userId, DisclosureField type, MeChangeProfileVisibilitySettingCommand command) {
        if (command.isVisible()) {
            disclosureAgreementRepository.upsert(userId, type.name());
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
                .orElseThrow(() -> new BusinessException(ErrorCode.APP_NOTIFICATION_NOT_FOUND));

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
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        MeStatusWithdrawalResult withdrawal = new MeStatusWithdrawalResult(
                user.getWithdrawalRequestedAt() != null,
                user.getWithdrawalScheduledAt()
        );

        List<Report> reports = reportRepository.findAllByReportedUserIdAndStatus(userId, ReportStatus.RESOLVED);
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
        LocalDate today = KstTimes.today();

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

        LocalDate date = duration == HesitationDuration.TODAY ? today : null;
        return new MeHesitationsResult(date, hesitationIds);
    }

    @Transactional
    public void hesitationsAnswer(Long userId, Long hesitationId, MeHesitationsAnswerCommand command) {
        Question question = questionRepository.findById(hesitationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HESITATION_NOT_FOUND));

        if (!question.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_HESITATION_OWNER);
        }

        if (question.getAnsweredAt() != null || Boolean.TRUE.equals(question.getIsSkipped())) {
            if (isSameHandling(question, command)) {
                return;
            }
            throw new BusinessException(ErrorCode.HESITATION_ALREADY_HANDLED);
        }

        if (Boolean.TRUE.equals(command.skipped())) {
            question.skip();
            return;
        }

        if (command.answer() == null || command.answer().isBlank()) {
            throw new BusinessException(ErrorCode.HESITATION_ANSWER_EMPTY);
        }

        if (!question.getOptions().contains(command.answer())) {
            throw new BusinessException(ErrorCode.HESITATION_ANSWER_NOT_IN_OPTIONS);
        }

        question.answer(command.answer());
    }

    public MeProfileResult profile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        ProfilePhotoInfo profilePhoto = photoRepository.findByUserIdAndType(userId, PhotoType.PROFILE)
                .map(photo -> new ProfilePhotoInfo(photo.getKey(), cloudFrontService.getSignedUrl(photo.getKey()), photo.position()))
                .orElse(null);

        Map<PersonaDimension, List<String>> explanationsByDimension = personaElementRepository.findAllByUserIdOrderByIdAsc(userId).stream()
                .collect(Collectors.groupingBy(
                        PersonaElement::getDimension,
                        LinkedHashMap::new,
                        Collectors.mapping(PersonaElement::getExplanation, Collectors.toList())
                ));

        List<Long> partnerUserIds = encounterRepository.findAllPartnerUserIdsByUserId(userId);

        return new MeProfileResult(
                user.getId(),
                user.displayFullName(),
                profilePhoto,
                persona(explanationsByDimension),
                explanationsByDimension.getOrDefault(PersonaDimension.INTEREST, List.of()),
                partnerUserIds.size(),
                encounteredFriendCount(userId, partnerUserIds)
        );
    }

    private String persona(Map<PersonaDimension, List<String>> explanationsByDimension) {
        List<String> summaries = explanationsByDimension.getOrDefault(PersonaDimension.SUMMARY, List.of());
        if (!summaries.isEmpty()) {
            return summaries.getLast();
        }

        List<String> explanations = PERSONA_SUMMARY_DIMENSIONS.stream()
                .map(dimension -> explanationsByDimension.getOrDefault(dimension, List.of()))
                .filter(dimensionExplanations -> !dimensionExplanations.isEmpty())
                .map(List::getFirst)
                .limit(PERSONA_SUMMARY_SIZE)
                .toList();

        return String.join(PERSONA_SUMMARY_DELIMITER, explanations) + PERSONA_SUMMARY_SUFFIX;
    }

    private int encounteredFriendCount(Long userId, List<Long> partnerUserIds) {
        if (partnerUserIds.isEmpty()) {
            return 0;
        }

        return (int) relationshipRepository.findLatestByUserIdAndPartnerUserIdIn(userId, partnerUserIds).stream()
                .filter(relationship -> RelationshipType.fromIntimacy(relationship.getIntimacy()) != RelationshipType.ACQUAINTANCE)
                .count();
    }

    private boolean isSameHandling(Question question, MeHesitationsAnswerCommand command) {
        if (Boolean.TRUE.equals(command.skipped())) {
            return Boolean.TRUE.equals(question.getIsSkipped());
        }

        return question.getAnsweredAt() != null && Objects.equals(question.getChoice(), command.answer());
    }

    public MePurchasesResult purchases(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Instant now = Instant.now();
        List<String> entitlements = userEntitlementRepository.findAllByUserId(userId).stream()
                .filter(userEntitlement -> userEntitlement.isActiveAt(now))
                .map(UserEntitlement::getEntitlement)
                .toList();

        return new MePurchasesResult(user.getRevenueCatUserId(), entitlements);
    }
}
