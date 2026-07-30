package com.nidus.twinly.people.service;

import com.nidus.twinly.activity.domain.SceneType;
import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.entity.ScenePartner;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.block.repository.BlockRepository;
import com.nidus.twinly.chat.entity.ChatRoom;
import com.nidus.twinly.chat.repository.ChatRoomRepository;
import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.common.photo.ProfilePhotoInfo;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.match.entity.Match;
import com.nidus.twinly.match.repository.MatchRepository;
import com.nidus.twinly.people.dto.result.*;
import com.nidus.twinly.people.domain.IntimacyResolution;
import com.nidus.twinly.people.entity.Encounter;
import com.nidus.twinly.people.entity.EncounterPreference;
import com.nidus.twinly.people.repository.EncounterPreferenceRepository;
import com.nidus.twinly.people.repository.EncounterRepository;
import com.nidus.twinly.relationship.domain.RelationshipSpecificType;
import com.nidus.twinly.relationship.domain.RelationshipType;
import com.nidus.twinly.relationship.entity.Relationship;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.user.domain.DisclosureField;
import com.nidus.twinly.user.entity.DisclosureAgreement;
import com.nidus.twinly.user.entity.Photo;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.DisclosureAgreementRepository;
import com.nidus.twinly.user.repository.PhotoRepository;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PeopleService {

    private static final int DEFAULT_LIMIT = 20;

    private final RelationshipRepository relationshipRepository;
    private final UserRepository userRepository;
    private final PhotoRepository photoRepository;
    private final MatchRepository matchRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ScenePartnerRepository scenePartnerRepository;
    private final SceneRepository sceneRepository;
    private final EncounterRepository encounterRepository;
    private final EncounterPreferenceRepository encounterPreferenceRepository;
    private final DisclosureAgreementRepository disclosureAgreementRepository;
    private final BlockRepository blockRepository;

    private final CloudFrontService cloudFrontService;
    private final ObjectMapper objectMapper;

    public PeopleResult people(Long userId, Long cursor, Integer limit) {
        int effectiveLimit = (limit != null && limit > 0) ? limit : DEFAULT_LIMIT;

        List<Long> fetched = relationshipRepository.findPartnerUserIdsByUserId(userId, cursor, effectiveLimit + 1);

        boolean hasMore = fetched.size() > effectiveLimit;
        List<Long> partnerUserIds = hasMore ? fetched.subList(0, effectiveLimit) : fetched;

        if (partnerUserIds.isEmpty()) {
            return new PeopleResult(List.of(), new PeoplePageResult(null, false));
        }

        Map<Long, User> userByPartnerUserId = userRepository.findAllById(partnerUserIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<Long> visiblePartnerUserIds = partnerUserIds.stream()
                .filter(partnerUserId -> !userByPartnerUserId.get(partnerUserId).isWithdrawn())
                .toList();

        Map<Long, ProfilePhotoInfo> profilePhotoByPartnerUserId = photoRepository.findAllByUserIdInAndType(visiblePartnerUserIds, PhotoType.PROFILE).stream()
                .collect(Collectors.toMap(Photo::getUserId, photo -> new ProfilePhotoInfo(photo.getKey(), cloudFrontService.getSignedUrl(photo.getKey()), photo.position())));

        Map<Long, Integer> intimacyByPartnerUserId = relationshipRepository.findLatestByUserIdAndPartnerUserIdIn(userId, partnerUserIds).stream()
                .collect(Collectors.toMap(Relationship::getPartnerUserId, Relationship::getIntimacy));

        List<Match> matches = matchRepository.findAllByUserIdAndPartnerUserIdIn(userId, partnerUserIds);
        Map<Long, Long> matchIdByPartnerUserId = matches.stream()
                .collect(Collectors.toMap(match -> partnerUserIdOf(match.getUserAId(), match.getUserBId(), userId), Match::getId));
        Map<Long, Long> roomIdByMatchId = chatRoomRepository.findAllByMatchIdIn(matches.stream().map(Match::getId).toList()).stream()
                .filter(room -> room.getClosedAt() == null)
                .collect(Collectors.toMap(ChatRoom::getMatchId, ChatRoom::getId));

        Map<Long, Integer> sceneCountByPartnerUserId = scenePartnerRepository.countScenesByUserIdAndPartnerUserIdIn(userId, partnerUserIds).stream()
                .collect(Collectors.toMap(ScenePartnerRepository.SceneCountProjection::getPartnerUserId, projection -> projection.getCount().intValue()));

        List<Encounter> encounters = encounterRepository.findAllByUserIdAndPartnerUserIdIn(userId, partnerUserIds);
        Map<Long, Long> encounterIdByPartnerUserId = encounters.stream()
                .collect(Collectors.toMap(encounter -> partnerUserIdOf(encounter.getUserAId(), encounter.getUserBId(), userId), Encounter::getId));
        Map<Long, Boolean> favoritedByEncounterId = encounterPreferenceRepository.findAllByEncounterIdInAndUserId(
                        encounters.stream().map(Encounter::getId).toList(), userId).stream()
                .collect(Collectors.toMap(EncounterPreference::getEncounterId, EncounterPreference::getIsFavorited));

        List<PeopleItemResult> people = partnerUserIds.stream()
                .map(partnerUserId -> {
                    User user = userByPartnerUserId.get(partnerUserId);
                    Integer intimacy = intimacyByPartnerUserId.getOrDefault(partnerUserId, 0);

                    Long matchId = matchIdByPartnerUserId.get(partnerUserId);
                    Long chatRoomId = matchId != null ? roomIdByMatchId.get(matchId) : null;

                    Long encounterId = encounterIdByPartnerUserId.get(partnerUserId);
                    boolean isFavorited = encounterId != null && Boolean.TRUE.equals(favoritedByEncounterId.get(encounterId));

                    return new PeopleItemResult(
                            partnerUserId,
                            user.displayGivenName(),
                            profilePhotoByPartnerUserId.get(partnerUserId),
                            intimacy,
                            RelationshipType.fromIntimacy(intimacy),
                            RelationshipSpecificType.fromIntimacy(intimacy),
                            sceneCountByPartnerUserId.getOrDefault(partnerUserId, 0),
                            chatRoomId,
                            isFavorited
                    );
                })
                .toList();

        Long nextCursor = hasMore ? partnerUserIds.get(partnerUserIds.size() - 1) : null;

        return new PeopleResult(people, new PeoplePageResult(nextCursor, hasMore));
    }

    private Long partnerUserIdOf(Long userAId, Long userBId, Long userId) {
        return userAId.equals(userId) ? userBId : userAId;
    }

    public PeopleProfileResult profile(Long userId, Long partnerUserId) {
        User partner = userRepository.findById(partnerUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        int intimacy = relationshipRepository.findLatestByUserIdAndPartnerUserId(userId, partnerUserId)
                .map(Relationship::getIntimacy)
                .orElse(0);

        boolean isFavorited = encounterRepository.findByUserAIdAndUserBId(Math.min(userId, partnerUserId), Math.max(userId, partnerUserId))
                .flatMap(encounter -> encounterPreferenceRepository.findByEncounterIdAndUserId(encounter.getId(), userId))
                .map(EncounterPreference::getIsFavorited)
                .orElse(false);

        boolean isBlocked = blockRepository.existsByUserIdAndBlockedUserId(userId, partnerUserId);

        ProfilePhotoInfo profilePhoto = partner.isWithdrawn() ? null
                : photoRepository.findByUserIdAndType(partnerUserId, PhotoType.PROFILE)
                        .map(photo -> new ProfilePhotoInfo(photo.getKey(), cloudFrontService.getSignedUrl(photo.getKey()), photo.position()))
                        .orElse(null);

        Set<DisclosureField> disclosedFields = partner.isWithdrawn() ? Set.of()
                : disclosureAgreementRepository.findAllByUserId(partnerUserId).stream()
                        .map(DisclosureAgreement::getField)
                        .collect(Collectors.toSet());

        PeopleProfileDisclosedFieldsResult disclosed = new PeopleProfileDisclosedFieldsResult(
                disclosedFields.contains(DisclosureField.AFFILIATION) ? partner.getAffiliation() : null,
                disclosedFields.contains(DisclosureField.AFFILIATION_NUMBER) ? partner.getAffiliationNumber() : null
        );

        return new PeopleProfileResult(
                partnerUserId,
                partner.displayName(),
                profilePhoto,
                intimacy,
                RelationshipType.fromIntimacy(intimacy),
                RelationshipSpecificType.fromIntimacy(intimacy),
                isFavorited,
                disclosed,
                partner.getDeletedAt() != null,
                isBlocked
        );
    }

    @Transactional
    public void favorite(Long userId, Long partnerUserId) {
        Encounter encounter = encounterRepository.findByUserAIdAndUserBId(Math.min(userId, partnerUserId), Math.max(userId, partnerUserId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ENCOUNTER_NOT_FOUND));

        EncounterPreference preference = encounterPreferenceRepository.findByEncounterIdAndUserId(encounter.getId(), userId)
                .orElseGet(() -> EncounterPreference.create(encounter.getId(), userId));

        preference.changeIsFavorited(true);
        encounterPreferenceRepository.save(preference);
    }

    @Transactional
    public void deleteFavorite(Long userId, Long partnerUserId) {
        Encounter encounter = encounterRepository.findByUserAIdAndUserBId(Math.min(userId, partnerUserId), Math.max(userId, partnerUserId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ENCOUNTER_NOT_FOUND));

        encounterPreferenceRepository.findByEncounterIdAndUserId(encounter.getId(), userId)
                .ifPresent(preference -> {
                    preference.changeIsFavorited(false);
                    encounterPreferenceRepository.save(preference);
                });
    }

    public PeopleIntimacySeriesResult intimacySeries(Long userId, Long partnerUserId, LocalDate from, LocalDate to, IntimacyResolution resolution, Integer maxPoints) {
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE, "조회 시작일이 종료일보다 늦습니다: from=%s, to=%s".formatted(from, to));
        }

        List<Relationship> relationships = relationshipRepository
                .findAllByUserIdAndPartnerUserIdAndDateBetweenOrderByDateAsc(userId, partnerUserId, from, to);

        List<PeopleIntimacySeriesItemResult> series = downsample(bucketByResolution(relationships, resolution, from), maxPoints);

        int currentIntimacy = relationshipRepository.findLatestByUserIdAndPartnerUserId(userId, partnerUserId)
                .map(Relationship::getIntimacy)
                .orElseThrow(() -> new BusinessException(ErrorCode.RELATIONSHIP_NOT_FOUND));

        return new PeopleIntimacySeriesResult(currentIntimacy, series);
    }

    private List<PeopleIntimacySeriesItemResult> bucketByResolution(List<Relationship> relationships, IntimacyResolution resolution, LocalDate from) {
        if (resolution == IntimacyResolution.WEEK) {
            Map<LocalDate, List<Relationship>> byWeek = relationships.stream()
                    .collect(Collectors.groupingBy(
                            relationship -> from.plusDays(ChronoUnit.DAYS.between(from, relationship.getDate()) / 7 * 7),
                            LinkedHashMap::new,
                            Collectors.toList()));

            return byWeek.entrySet().stream()
                    .map(entry -> new PeopleIntimacySeriesItemResult(
                            entry.getKey(),
                            entry.getValue().get(0).getIntimacy()))
                    .toList();
        }

        return relationships.stream()
                .map(relationship -> new PeopleIntimacySeriesItemResult(relationship.getDate(), relationship.getIntimacy()))
                .toList();
    }

    private List<PeopleIntimacySeriesItemResult> downsample(List<PeopleIntimacySeriesItemResult> series, Integer maxPoints) {
        if (series.size() <= maxPoints) {
            return series;
        }

        List<PeopleIntimacySeriesItemResult> sampled = new ArrayList<>(maxPoints);
        double step = (double) series.size() / maxPoints;
        for (int i = 0; i < maxPoints; i++) {
            int index = Math.min((int) Math.floor((i + 1) * step) - 1, series.size() - 1);
            sampled.add(series.get(index));
        }
        return sampled;
    }

    public PeopleEventsResult events(Long userId, Long partnerUserId, LocalDate cursor, Integer limit) {
        User partner = userRepository.findById(partnerUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        int intimacy = relationshipRepository.findLatestByUserIdAndPartnerUserId(userId, partnerUserId)
                .map(Relationship::getIntimacy)
                .orElse(0);

        ProfilePhotoInfo profilePhoto = partner.isWithdrawn() ? null
                : photoRepository.findByUserIdAndType(partnerUserId, PhotoType.PROFILE)
                        .map(photo -> new ProfilePhotoInfo(photo.getKey(), cloudFrontService.getSignedUrl(photo.getKey()), photo.position()))
                        .orElse(null);

        PeopleEventsPartnerResult partnerResult = new PeopleEventsPartnerResult(
                partnerUserId,
                partner.displayName(),
                profilePhoto,
                intimacy,
                RelationshipSpecificType.fromIntimacy(intimacy)
        );

        int effectiveLimit = (limit != null && limit > 0) ? limit : DEFAULT_LIMIT;

        List<LocalDate> fetchedDates = sceneRepository.findDistinctDatesFromCursorByUserIdAndWithPartnerUserId(userId, partnerUserId, cursor, effectiveLimit + 1);
        boolean hasMore = fetchedDates.size() > effectiveLimit;
        List<LocalDate> pageDates = hasMore ? fetchedDates.subList(0, effectiveLimit) : fetchedDates;

        Map<LocalDate, List<Scene>> scenesByDate = pageDates.isEmpty()
                ? Map.of()
                : sceneRepository.findAllByUserIdAndWithPartnerUserIdAndDateIn(userId, partnerUserId, pageDates).stream()
                        .collect(Collectors.groupingBy(Scene::getDate, LinkedHashMap::new, Collectors.toList()));

        Map<LocalDate, Integer> deltaByDate = new HashMap<>();
        Map<LocalDate, String> changeByDate = new HashMap<>();
        Relationship previous = null;
        for (Relationship current : relationshipRepository.findAllByUserIdAndPartnerUserIdOrderByDateAsc(userId, partnerUserId)) {
            if (previous != null) {
                deltaByDate.put(current.getDate(), current.getIntimacy() - previous.getIntimacy());

                RelationshipSpecificType previousType = RelationshipSpecificType.fromIntimacy(previous.getIntimacy());
                RelationshipSpecificType currentType = RelationshipSpecificType.fromIntimacy(current.getIntimacy());
                if (currentType != previousType) {
                    changeByDate.put(current.getDate(), currentType.name());
                }
            }
            previous = current;
        }

        List<PeopleEventsItemResult> events = pageDates.stream()
                .map(date -> {
                    Scene firstScene = scenesByDate.get(date).get(0);
                    return new PeopleEventsItemResult(
                            date,
                            changeByDate.get(date),
                            deltaByDate.get(date),
                            firstScene.getPlace(),
                            preview(firstScene)
                    );
                })
                .toList();

        LocalDate nextCursor = hasMore ? pageDates.get(pageDates.size() - 1) : null;

        return new PeopleEventsResult(partnerResult, events, new PeopleEventsPageResult(nextCursor, hasMore));
    }

    private String preview(Scene scene) {
        if (scene.getType() == SceneType.ACTION) {
            return scene.getNarration();
        }

        List<PeopleEventLineResult> lines = parseLines(scene);
        if (lines.isEmpty()) {
            return null;
        }

        return switch (lines.get(0)) {
            case PeopleEventNarrationLineResult narration -> narration.text();
            case PeopleEventBubbleLineResult bubble -> bubble.text();
        };
    }

    private List<PeopleEventLineResult> parseLines(Scene scene) {
        if (scene.getLines() == null) {
            return List.of();
        }

        try {
            return objectMapper.readValue(scene.getLines(), new TypeReference<List<PeopleEventLineResult>>() {
            });
        } catch (JacksonException e) {
            log.warn("씬 대사 파싱에 실패해 빈 목록으로 대체합니다. sceneId={}", scene.getId(), e);
            return List.of();
        }
    }

    public PeopleEventResult event(Long userId, Long partnerUserId, LocalDate date) {
        if (!userRepository.existsById(partnerUserId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        List<Scene> myScenes = sceneRepository.findAllByUserIdAndDate(userId, date);

        List<Long> sceneIds = myScenes.stream().map(Scene::getId).toList();
        List<ScenePartner> scenePartners = scenePartnerRepository.findAllBySceneIdIn(sceneIds);
        Map<Long, List<Long>> partnerUserIdsBySceneId = scenePartners.stream()
                .collect(Collectors.groupingBy(ScenePartner::getSceneId, Collectors.mapping(ScenePartner::getUserId, Collectors.toList())));

        List<Scene> scenes = myScenes.stream()
                .filter(scene -> partnerUserIdsBySceneId.getOrDefault(scene.getId(), List.of()).contains(partnerUserId))
                .toList();

        List<Long> speakerUserIds = scenePartners.stream().map(ScenePartner::getUserId).distinct().toList();
        Map<Long, User> userById = userRepository.findAllById(speakerUserIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<PeopleEventSceneResult> sceneResults = scenes.stream()
                .map(scene -> toSceneResult(scene, partnerUserIdsBySceneId.getOrDefault(scene.getId(), List.of()), userById))
                .toList();

        String version = scenes.isEmpty() ? null : scenes.get(0).getVersion();

        List<Long> withUserIds = scenes.stream()
                .flatMap(scene -> partnerUserIdsBySceneId.getOrDefault(scene.getId(), List.<Long>of()).stream())
                .distinct()
                .toList();

        return new PeopleEventResult(date, partnerUserId, version, sceneResults, toProfilePhotoResults(withUserIds, userById));
    }

    private List<PeopleEventProfilePhotoResult> toProfilePhotoResults(List<Long> userIds, Map<Long, User> userById) {
        if (userIds.isEmpty()) {
            return List.of();
        }

        List<Long> visibleUserIds = userIds.stream()
                .filter(userId -> !userById.get(userId).isWithdrawn())
                .toList();

        Map<Long, ProfilePhotoInfo> profilePhotoByUserId = photoRepository.findAllByUserIdInAndType(visibleUserIds, PhotoType.PROFILE).stream()
                .collect(Collectors.toMap(Photo::getUserId, photo -> new ProfilePhotoInfo(photo.getKey(), cloudFrontService.getSignedUrl(photo.getKey()), photo.position())));

        return userIds.stream()
                .map(userId -> new PeopleEventProfilePhotoResult(userId, profilePhotoByUserId.get(userId)))
                .toList();
    }

    private PeopleEventSceneResult toSceneResult(Scene scene, List<Long> partnerUserIds, Map<Long, User> userById) {
        List<PeopleEventSpeakerResult> with = partnerUserIds.stream()
                .map(partnerUserId -> {
                    User user = userById.get(partnerUserId);
                    return new PeopleEventSpeakerResult(partnerUserId, user.displayName());
                })
                .toList();

        OffsetDateTime startsAt = KstTimes.toKstOffsetDateTime(scene.getStartsAt());
        OffsetDateTime endsAt = KstTimes.toKstOffsetDateTime(scene.getEndsAt());

        return switch (scene.getType()) {
            case ACTION -> new PeopleEventActionSceneResult(
                    scene.getId(),
                    "action",
                    startsAt,
                    endsAt,
                    scene.getPlace(),
                    with,
                    scene.getNarration(),
                    scene.getMind()
            );
            case DIALOGUE -> new PeopleEventDialogueSceneResult(
                    scene.getId(),
                    "dialogue",
                    startsAt,
                    endsAt,
                    scene.getPlace(),
                    with,
                    parseLines(scene)
            );
        };
    }

    public PeopleLearnedFactsResult learnedFacts(Long userId, Long partnerUserId) {
        Relationship relationship = relationshipRepository.findLatestByUserIdAndPartnerUserId(userId, partnerUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RELATIONSHIP_NOT_FOUND));

        return new PeopleLearnedFactsResult(relationship.getPartnerModel());
    }
}
