package com.nidus.twinly.map.service;

import com.nidus.twinly.activity.domain.SceneType;
import com.nidus.twinly.activity.entity.ScenePartner;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.activity.repository.SceneRepository.SceneRouteProjection;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.map.dto.result.MapTwinResult;
import com.nidus.twinly.map.dto.result.MapTwinSceneResult;
import com.nidus.twinly.map.dto.result.MapTwinsResult;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.relationship.repository.RelationshipRepository.PartnerFirstUpdateProjection;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapService {

    private static final int MAX_TWINS = 20;
    private static final Duration SCENE_LOOKBACK = Duration.ofHours(24);
    private static final String ANONYMOUS_DISPLAY_NAME = "누군가의 트윈";

    private final SceneRepository sceneRepository;
    private final ScenePartnerRepository scenePartnerRepository;
    private final SeasonParticipationRepository seasonParticipationRepository;
    private final RelationshipRepository relationshipRepository;
    private final UserRepository userRepository;
    private final CurrentSeasonReader currentSeasonReader;

    public MapTwinsResult twins(Long userId) {
        OffsetDateTime now = OffsetDateTime.now(KstTimes.ZONE);
        LocalDate today = now.toLocalDate();
        LocalDateTime todayStart = today.atStartOfDay();
        SceneRange range = SceneRange.of(now, today);

        Set<Long> candidateUserIds = new HashSet<>(seasonParticipationRepository.findAllMapCandidateUserIds(
                userId,
                currentSeasonReader.read().getId(),
                KstTimes.toInstant(todayStart),
                range.fromDate(),
                range.toDate(),
                range.endsAfter()));

        if (candidateUserIds.isEmpty()) {
            return new MapTwinsResult(now, List.of());
        }

        List<PartnerFirstUpdateProjection> partners = relationshipRepository.findAllPartnerFirstUpdatesByUserId(userId);
        Set<Long> partnerUserIds = partners.stream()
                .map(PartnerFirstUpdateProjection::getPartnerUserId)
                .collect(Collectors.toSet());
        Set<Long> metUserIds = metUserIds(userId, range);
        Comparator<Long> dailyOrder = dailyOrder(userId, today);

        List<Long> relationshipUserIds = partners.stream()
                .filter(partner -> partner.getFirstUpdateTime().isBefore(todayStart))
                .map(PartnerFirstUpdateProjection::getPartnerUserId)
                .filter(candidateUserIds::contains)
                .sorted(dailyOrder)
                .limit(MAX_TWINS)
                .toList();

        List<Long> anonymousUserIds = candidateUserIds.stream()
                .filter(candidateUserId -> !partnerUserIds.contains(candidateUserId))
                .filter(candidateUserId -> !metUserIds.contains(candidateUserId))
                .sorted(dailyOrder)
                .limit(MAX_TWINS - relationshipUserIds.size())
                .toList();

        List<Long> pickedUserIds = Stream.concat(relationshipUserIds.stream(), anonymousUserIds.stream()).toList();
        if (pickedUserIds.isEmpty()) {
            return new MapTwinsResult(now, List.of());
        }

        Map<Long, List<MapTwinSceneResult>> scenesByUserId = sceneRepository.findAllRoutesByUserIdInAndEndsAfter(
                        pickedUserIds, range.fromDate(), range.toDate(), range.endsAfter()).stream()
                .filter(this::isDrawable)
                .collect(Collectors.groupingBy(SceneRouteProjection::getUserId,
                        Collectors.mapping(this::toSceneResult, Collectors.toList())));

        Map<Long, String> nicknameByUserId = userRepository.findAllById(relationshipUserIds).stream()
                .filter(user -> user.displayNickname() != null && !user.displayNickname().isBlank())
                .collect(Collectors.toMap(User::getId, User::displayNickname));

        List<MapTwinResult> twins = pickedUserIds.stream()
                .filter(scenesByUserId::containsKey)
                .map(pickedUserId -> new MapTwinResult(
                        pickedUserId,
                        nicknameByUserId.getOrDefault(pickedUserId, ANONYMOUS_DISPLAY_NAME),
                        scenesByUserId.get(pickedUserId)))
                .toList();

        return new MapTwinsResult(now, twins);
    }

    private Set<Long> metUserIds(Long userId, SceneRange range) {
        List<Long> sceneIds = sceneRepository.findAllRoutesByUserIdInAndEndsAfter(
                        List.of(userId), range.fromDate(), range.toDate(), range.endsAfter()).stream()
                .map(SceneRouteProjection::getId)
                .toList();

        if (sceneIds.isEmpty()) {
            return Set.of();
        }

        return scenePartnerRepository.findAllBySceneIdIn(sceneIds).stream()
                .map(ScenePartner::getUserId)
                .collect(Collectors.toSet());
    }

    private Comparator<Long> dailyOrder(Long userId, LocalDate date) {
        return Comparator.<Long>comparingLong(candidateUserId -> new SplittableRandom(Objects.hash(userId, date, candidateUserId)).nextLong())
                .thenComparing(Comparator.naturalOrder());
    }

    private boolean isDrawable(SceneRouteProjection scene) {
        return scene.getPlaceCode() != null && scene.getEndsAt().isAfter(scene.getStartsAt());
    }

    private MapTwinSceneResult toSceneResult(SceneRouteProjection scene) {
        boolean isMove = scene.getType() == SceneType.MOVE;

        return new MapTwinSceneResult(
                scene.getType().name().toLowerCase(Locale.ROOT),
                KstTimes.toKstOffsetDateTime(scene.getStartsAt()),
                KstTimes.toKstOffsetDateTime(scene.getEndsAt()),
                scene.getPlaceCode(),
                isMove ? scene.getFromPlaceCode() : null,
                isMove ? scene.getTravelMode() : null
        );
    }

    private record SceneRange(LocalDate fromDate, LocalDate toDate, LocalDateTime endsAfter) {

        private static SceneRange of(OffsetDateTime now, LocalDate today) {
            LocalDateTime endsAfter = now.minus(SCENE_LOOKBACK).toLocalDateTime();

            return new SceneRange(endsAfter.toLocalDate().minusDays(1), today, endsAfter);
        }
    }
}
