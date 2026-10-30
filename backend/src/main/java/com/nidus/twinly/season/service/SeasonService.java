package com.nidus.twinly.season.service;

import com.nidus.twinly.block.repository.BlockRepository;
import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.common.logging.InfoLog;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.common.photo.ProfilePhotoInfo;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.season.domain.SeasonRecapData;
import com.nidus.twinly.season.dto.command.SeasonChangeCommand;
import com.nidus.twinly.season.dto.result.SeasonChangeResult;
import com.nidus.twinly.season.dto.result.SeasonLatestRecapActivityResult;
import com.nidus.twinly.season.dto.result.SeasonLatestRecapActivityTypeCountsResult;
import com.nidus.twinly.season.dto.result.SeasonLatestRecapActivityTypeResult;
import com.nidus.twinly.season.dto.result.SeasonLatestRecapBestMatchResult;
import com.nidus.twinly.season.dto.result.SeasonLatestRecapClosestResult;
import com.nidus.twinly.season.dto.result.SeasonLatestRecapCountResult;
import com.nidus.twinly.season.dto.result.SeasonLatestRecapMinutesResult;
import com.nidus.twinly.season.dto.result.SeasonLatestRecapMostMetResult;
import com.nidus.twinly.season.dto.result.SeasonLatestRecapPlaceResult;
import com.nidus.twinly.season.dto.result.SeasonLatestRecapResult;
import com.nidus.twinly.season.dto.result.SeasonLatestRecapTogetherResult;
import com.nidus.twinly.season.dto.result.SeasonParticipationResult;
import com.nidus.twinly.season.dto.result.SeasonPoolReassignResult;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.entity.SeasonParticipation;
import com.nidus.twinly.season.entity.SeasonRecap;
import com.nidus.twinly.season.event.SeasonChangedEvent;
import com.nidus.twinly.purchase.domain.PoolAssignment;
import com.nidus.twinly.purchase.service.PurchaseService;
import com.nidus.twinly.purchase.writer.PurchaseWriter;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import com.nidus.twinly.season.repository.SeasonRecapRepository;
import com.nidus.twinly.season.repository.SeasonRepository;
import com.nidus.twinly.season.writer.SeasonParticipationWriter;
import com.nidus.twinly.season.writer.SeasonRowLinker;
import com.nidus.twinly.legal.domain.PolicyKind;
import com.nidus.twinly.legal.reader.ConsentReader;
import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.user.entity.Photo;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PhotoRepository;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeasonService {

    private final CurrentSeasonReader currentSeasonReader;
    private final SeasonParticipationRepository seasonParticipationRepository;
    private final SeasonRepository seasonRepository;
    private final SeasonRowLinker seasonRowLinker;
    private final SeasonParticipationWriter seasonParticipationWriter;
    private final ApplicationEventPublisher eventPublisher;
    private final PurchaseService purchaseService;
    private final PurchaseWriter purchaseWriter;
    private final EntitlementReader entitlementReader;
    private final ConsentReader consentReader;
    private final UserRepository userRepository;
    private final SeasonRecapRepository seasonRecapRepository;
    private final PhotoRepository photoRepository;
    private final CloudFrontService cloudFrontService;
    private final BlockRepository blockRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public SeasonChangeResult changeSeason(SeasonChangeCommand command) {
        if (!command.startedAt().isBefore(command.endedAt())) {
            throw new BusinessException(ErrorCode.INVALID_SEASON_PERIOD);
        }

        seasonRepository.findFirstByIsActiveFalseAndActivatedAtIsNullOrderByStartedAtAsc().ifPresent(reserved -> {
            seasonRowLinker.unlink(reserved);
            seasonRepository.delete(reserved);
        });
        seasonRepository.findAllByIsActiveTrue().forEach(Season::deactivate);

        Season season = seasonRepository.save(Season.create(command.startedAt(), command.endedAt()));

        finishActivation(season);

        return new SeasonChangeResult(season.getId(), season.getStartedAt(), season.getEndedAt());
    }

    @Transactional
    public void activateReserved(Season reserved, Instant now) {
        seasonRepository.findAllByIsActiveTrue().forEach(Season::deactivate);

        reserved.activate(now);

        finishActivation(reserved);
    }

    private void finishActivation(Season season) {
        seasonParticipationWriter.participateAllEligible(season.getId());

        seasonRowLinker.link(season);

        eventPublisher.publishEvent(new SeasonChangedEvent(season.getId()));
    }

    @Transactional
    public SeasonPoolReassignResult reassignPools() {
        Long seasonId = seasonRepository.findFirstByIsActiveFalseAndActivatedAtIsNullOrderByStartedAtAsc()
                .map(Season::getId)
                .orElseGet(() -> currentSeasonReader.read().getId());

        return reassignPools(seasonId);
    }

    @Transactional
    public SeasonPoolReassignResult reassignPools(Long seasonId) {
        Season season = seasonRepository.findWithLockById(seasonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SEASON_NOT_FOUND));

        validatePoolReassignTarget(season);

        if (season.getPoolsReassignedAt() != null) {
            InfoLog.log(log, "이미 풀을 재배정한 시즌이라 건너뜁니다.", field("seasonId", seasonId));

            return SeasonPoolReassignResult.of(purchaseWriter.currentPoolAssignments(), true);
        }

        List<PoolAssignment> assignments = purchaseWriter.reassignPools(seasonParticipationWriter.eligibleUserIds());

        if (!assignments.isEmpty()) {
            season.markPoolsReassigned(Instant.now());
        }

        SeasonPoolReassignResult result = SeasonPoolReassignResult.of(assignments, false);

        InfoLog.log(log, "시즌 풀을 다시 배정했습니다.", field("seasonId", seasonId),
                field("assignedCount", result.assignedCount()), field("poolCount", result.pools().size()));

        return result;
    }

    private void validatePoolReassignTarget(Season season) {
        boolean reserved = !season.getIsActive() && season.getActivatedAt() == null;
        boolean activeWithoutReservation = season.getIsActive() && !seasonRepository.existsByIsActiveFalseAndActivatedAtIsNull();

        if (!reserved && !activeWithoutReservation) {
            throw new BusinessException(ErrorCode.SEASON_POOL_REASSIGN_NOT_ALLOWED);
        }
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public void participateIn(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!user.isSchoolVerificationApproved()) {
            throw new BusinessException(ErrorCode.SCHOOL_VERIFICATION_NOT_APPROVED);
        }

        purchaseService.syncQuietly(user);

        if (!entitlementReader.hasSimulationAccess(userId)) {
            throw new BusinessException(ErrorCode.SIMULATION_ACCESS_REQUIRED);
        }
        if (!consentReader.hasAgreedAllRequired(userId, PolicyKind.PARALLEL_ENTRY)) {
            throw new BusinessException(ErrorCode.SIMULATION_CONSENT_REQUIRED);
        }

        seasonParticipationWriter.participateInCurrentSeason(userId);
    }

    public SeasonParticipationResult participation(Long userId) {
        userRepository.findById(userId).ifPresent(purchaseService::syncIfStale);

        Long currentSeasonId = currentSeasonReader.read().getId();

        Instant participatedInAt = seasonParticipationRepository.findByUserIdAndSeasonId(userId, currentSeasonId)
                .map(SeasonParticipation::getParticipatedInAt)
                .orElse(null);

        return new SeasonParticipationResult(currentSeasonId, participatedInAt);
    }

    public SeasonLatestRecapResult latestRecap(Long userId) {
        SeasonRecap recap = seasonRecapRepository.findServingByUserId(userId, currentSeasonReader.read().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SEASON_RECAP_NOT_FOUND));

        SeasonRecapData data = objectMapper.readValue(recap.getData(), SeasonRecapData.class);

        List<Long> partnerUserIds = Stream.of(
                        data.mostMet().stream().map(SeasonRecapData.MostMet::userId),
                        data.closest().stream().map(SeasonRecapData.Closest::userId),
                        data.bestMatches().stream().map(SeasonRecapData.BestMatch::userId))
                .flatMap(Function.identity())
                .distinct()
                .toList();

        Map<Long, User> visibleUserById = visibleUsers(userId, partnerUserIds);
        Map<Long, ProfilePhotoInfo> profilePhotoByUserId = visibleUserById.isEmpty()
                ? Map.of()
                : photoRepository.findAllByUserIdInAndType(List.copyOf(visibleUserById.keySet()), PhotoType.PROFILE).stream()
                        .collect(Collectors.toMap(Photo::getUserId,
                                photo -> new ProfilePhotoInfo(photo.getKey(), cloudFrontService.getSignedUrl(photo.getKey()), photo.position())));

        return new SeasonLatestRecapResult(
                recap.getSeasonId(),
                data.startedAt(),
                data.endedAt(),
                data.population(),
                new SeasonLatestRecapTogetherResult(data.togetherDays()),
                new SeasonLatestRecapActivityResult(
                        minutes(data.dialogueMinutes()),
                        minutes(data.actionMinutes()),
                        minutes(data.moveMinutes())),
                count(data.bubbles()),
                data.places().stream()
                        .map(place -> new SeasonLatestRecapPlaceResult(place.placeCode(), place.name(), place.visitCount(), place.minutes()))
                        .toList(),
                count(data.talked()),
                count(data.friends()),
                data.mostMet().stream()
                        .filter(item -> visibleUserById.containsKey(item.userId()))
                        .map(item -> new SeasonLatestRecapMostMetResult(item.userId(), visibleUserById.get(item.userId()).displayNickname(),
                                profilePhotoByUserId.get(item.userId()), item.days(), item.sceneCount()))
                        .toList(),
                data.closest().stream()
                        .filter(item -> visibleUserById.containsKey(item.userId()))
                        .map(item -> new SeasonLatestRecapClosestResult(item.userId(), visibleUserById.get(item.userId()).displayNickname(),
                                profilePhotoByUserId.get(item.userId()), item.twinIntimacy()))
                        .toList(),
                data.bestMatches().stream()
                        .filter(item -> visibleUserById.containsKey(item.userId()))
                        .map(item -> new SeasonLatestRecapBestMatchResult(item.userId(), visibleUserById.get(item.userId()).displayNickname(),
                                profilePhotoByUserId.get(item.userId()), item.matchRate(), item.comparedCount(), item.matchedCount()))
                        .toList(),
                count(data.profileViewers()),
                activityType(data.activityType())
        );
    }

    private Map<Long, User> visibleUsers(Long userId, List<Long> partnerUserIds) {
        if (partnerUserIds.isEmpty()) {
            return Map.of();
        }

        Set<Long> blockedUserIds = new HashSet<>();
        blockRepository.findAllByUserId(userId).forEach(block -> blockedUserIds.add(block.getBlockedUserId()));
        blockRepository.findAllByUserIdInAndBlockedUserId(partnerUserIds, userId).forEach(block -> blockedUserIds.add(block.getUserId()));

        return userRepository.findAllById(partnerUserIds).stream()
                .filter(user -> !user.isWithdrawn())
                .filter(user -> !blockedUserIds.contains(user.getId()))
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private static SeasonLatestRecapMinutesResult minutes(SeasonRecapData.Ranked ranked) {
        return new SeasonLatestRecapMinutesResult(ranked.value(), ranked.topPercent());
    }

    private static SeasonLatestRecapCountResult count(SeasonRecapData.Ranked ranked) {
        return new SeasonLatestRecapCountResult(ranked.value(), ranked.topPercent());
    }

    private static SeasonLatestRecapActivityTypeResult activityType(SeasonRecapData.ActivityType activityType) {
        if (activityType == null) {
            return null;
        }

        return new SeasonLatestRecapActivityTypeResult(
                activityType.type(),
                new SeasonLatestRecapActivityTypeCountsResult(
                        activityType.morning(),
                        activityType.lunch(),
                        activityType.evening(),
                        activityType.lateNight()));
    }
}
