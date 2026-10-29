package com.nidus.twinly.showcase.service;

import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.entity.ScenePartner;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.common.logging.WarnLog;
import com.nidus.twinly.common.scene.SceneBubbleLine;
import com.nidus.twinly.common.scene.SceneLine;
import com.nidus.twinly.common.scene.SceneNameRenderer;
import com.nidus.twinly.common.scene.SceneNarrationLine;
import com.nidus.twinly.common.scene.StoredSceneLine;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import com.nidus.twinly.showcase.dto.result.*;
import com.nidus.twinly.showcase.entity.Showcase;
import com.nidus.twinly.showcase.entity.ShowcaseUserRef;
import com.nidus.twinly.showcase.repository.ShowcaseRepository;
import com.nidus.twinly.showcase.repository.ShowcaseUserRefRepository;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import com.nidus.twinly.world.domain.TrackWindow;
import com.nidus.twinly.world.entity.WorldTrack;
import com.nidus.twinly.world.reader.WorldTrackSlice;
import com.nidus.twinly.world.service.WorldActorProfile;
import com.nidus.twinly.world.service.WorldService;
import com.nidus.twinly.world.service.WorldTrackMerger;
import com.nidus.twinly.world.service.WorldTrackPublisher;
import com.nidus.twinly.world.service.WorldTrackView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShowcaseService {

    private static final long TARGET_USER_REF = 1L;
    private static final String TARGET_WORLD_REF = WorldService.toUserRef(TARGET_USER_REF);
    private static final String MASKED_GIVEN_NAME = "OO";
    private static final int TOTAL_USER_COUNT_OFFSET = 70;
    private static final int SAME_ORGANIZATION_USER_COUNT_OFFSET = 2;
    private static final List<String> PSEUDONYM_FAMILY_NAMES = List.of(
            "김", "이", "박", "최", "정", "강", "조", "윤", "장", "임",
            "한", "오", "서", "신", "권", "황", "안", "송", "류", "홍"
    );

    private final ShowcaseRepository showcaseRepository;
    private final SceneRepository sceneRepository;
    private final ScenePartnerRepository scenePartnerRepository;
    private final ShowcaseUserRefRepository showcaseUserRefRepository;
    private final UserRepository userRepository;
    private final CurrentSeasonReader currentSeasonReader;
    private final SceneNameRenderer sceneNameRenderer;
    private final ShowcaseOutsiderRefs showcaseOutsiderRefs;
    private final WorldService worldService;
    private final WorldTrackMerger worldTrackMerger;
    private final WorldTrackPublisher worldTrackPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public ShowcaseTodayResult today(Long userId) {
        ShowcaseState state = resolve(userId);
        Set<Long> displayedUserIds = displayedUserIds(state.userRefByUserId(), state.scenes());
        Map<Long, User> userById = userRepository.findAllById(displayedUserIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, String> nameByUserId = maskedNameByUserId(state.showcase().getId(), state.userRefByUserId(), displayedUserIds, userById);

        return new ShowcaseTodayResult(
                state.showcase().getId(),
                state.showcase().getRefVersion(),
                TARGET_USER_REF,
                state.date(),
                Instant.now(),
                state.scenes().stream()
                        .map(scene -> toSceneResult(scene, state.partnerUserIdsBySceneId().get(scene.getId()), state.sceneLinesBySceneId().get(scene.getId()), state.userRefByUserId(), nameByUserId))
                        .toList(),
                toUserInfoResults(state.userRefByUserId(), userById, nameByUserId),
                toUserCountsResult(userId)
        );
    }

    @Transactional
    public ShowcaseTodaySectorsResult todaySectors(Long userId, String sectorCode, LocalDate date, String rawFrom, String rawTo) {
        TrackWindow window = TrackWindow.of(date, rawFrom, rawTo);
        ShowcaseState state = resolve(userId);
        Showcase showcase = state.showcase();

        User target = userRepository.findById(showcase.getTargetUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.WORLD_TRACK_NOT_FOUND));
        if (target.getPoolNumber() == null || !sceneRepository.existsByUserIdAndDate(target.getId(), date)) {
            throw new BusinessException(ErrorCode.WORLD_TRACK_NOT_FOUND);
        }

        WorldTrackSlice publicTrack = worldService.readTrack(target.getPoolNumber(), date, sectorCode, WorldTrack.PUBLIC_VIEWER_USER_ID, window);
        if (publicTrack == null) {
            throw new BusinessException(ErrorCode.WORLD_TRACK_NOT_FOUND);
        }

        Set<Long> listedUserIds = new LinkedHashSet<>(state.userRefByUserId().keySet());
        Map<Long, User> listedUserById = userRepository.findAllById(listedUserIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, String> nameByUserId = maskedNameByUserId(showcase.getId(), state.userRefByUserId(), listedUserIds, listedUserById);

        WorldTrackView view = worldTrackMerger.merge(publicTrack, null, TARGET_WORLD_REF, Set.of(), window);
        Map<Long, User> userById = userRepository.findAllById(WorldService.userIdsOf(view)).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        worldTrackPublisher.publish(view,
                ref -> worldProfile(WorldService.toUserId(ref), nameByUserId, userById),
                ref -> toShowcaseWorldRef(ref, showcase.getId(), state.userRefByUserId()));

        return new ShowcaseTodaySectorsResult(
                view.poolNumber(),
                date,
                sectorCode,
                view.layoutVersion(),
                OffsetDateTime.now(KstTimes.ZONE),
                TARGET_WORLD_REF,
                showcase.getId(),
                showcase.getRefVersion(),
                view.actors(),
                view.talks(),
                view.gestures(),
                view.objects(),
                view.events(),
                view.items()
        );
    }

    private ShowcaseState resolve(Long userId) {
        LocalDate date = KstTimes.today();
        Showcase showcase = showcaseRepository.findWithLockByViewerUserIdAndDate(userId, date)
                .orElseGet(() -> showcaseRepository.save(Showcase.create(userId, pickTargetUserId(userId, date), date)));

        List<Scene> scenes = sceneRepository.findAllByUserIdAndDateOrderByStartsAtAsc(showcase.getTargetUserId(), date);
        if (scenes.isEmpty()) {
            WarnLog.log(log, "관람 대상의 오늘 씬이 없어 대상을 다시 배정합니다.", field("showcaseId", showcase.getId()), field("targetUserId", showcase.getTargetUserId()));
            showcase.changeTarget(pickTargetUserId(userId, date));
            showcaseUserRefRepository.deleteAllByShowcaseId(showcase.getId());
            scenes = sceneRepository.findAllByUserIdAndDateOrderByStartsAtAsc(showcase.getTargetUserId(), date);
        }
        Map<Long, List<Long>> partnerUserIdsBySceneId = partnerUserIdsBySceneId(scenes);
        Map<Long, List<SceneLine>> sceneLinesBySceneId = sceneLinesBySceneId(scenes);
        Map<Long, Long> userRefByUserId = assignUserRefs(showcase, scenes, partnerUserIdsBySceneId, sceneLinesBySceneId);

        return new ShowcaseState(showcase, date, scenes, partnerUserIdsBySceneId, sceneLinesBySceneId, userRefByUserId);
    }

    private Long pickTargetUserId(Long viewerUserId, LocalDate date) {
        Long seasonId = currentSeasonReader.read().getId();
        List<Long> candidateUserIds = findCandidateUserIds(viewerUserId, seasonId, date, true);
        if (candidateUserIds.isEmpty()) {
            candidateUserIds = findCandidateUserIds(viewerUserId, seasonId, date, false);
        }

        if (candidateUserIds.isEmpty()) {
            throw new BusinessException(ErrorCode.SHOWCASE_TARGET_NOT_FOUND);
        }

        return candidateUserIds.get(ThreadLocalRandom.current().nextInt(candidateUserIds.size()));
    }

    private List<Long> findCandidateUserIds(Long viewerUserId, Long seasonId, LocalDate date, boolean showcaseSeedOnly) {
        List<Long> candidateUserIds = showcaseRepository.findAllTargetCandidateUserIds(viewerUserId, seasonId, date, true, showcaseSeedOnly);
        if (candidateUserIds.isEmpty()) {
            candidateUserIds = showcaseRepository.findAllTargetCandidateUserIds(viewerUserId, seasonId, date, false, showcaseSeedOnly);
        }

        return candidateUserIds;
    }

    private Map<Long, List<Long>> partnerUserIdsBySceneId(List<Scene> scenes) {
        List<Long> sceneIds = scenes.stream().map(Scene::getId).toList();

        return scenePartnerRepository.findAllBySceneIdIn(sceneIds).stream()
                .collect(Collectors.groupingBy(ScenePartner::getSceneId, Collectors.mapping(ScenePartner::getUserId, Collectors.toList())));
    }

    private Map<Long, List<SceneLine>> sceneLinesBySceneId(List<Scene> scenes) {
        return scenes.stream()
                .collect(Collectors.toMap(Scene::getId, this::toSceneLines));
    }

    private Map<Long, Long> assignUserRefs(Showcase showcase,
                                           List<Scene> scenes,
                                           Map<Long, List<Long>> partnerUserIdsBySceneId,
                                           Map<Long, List<SceneLine>> sceneLinesBySceneId) {
        Map<Long, Long> storedRefByUserId = showcaseUserRefRepository.findAllByShowcaseId(showcase.getId()).stream()
                .collect(Collectors.toMap(ShowcaseUserRef::getUserId, ShowcaseUserRef::getUserRef));
        long nextRef = storedRefByUserId.values().stream().mapToLong(Long::longValue).max().orElse(TARGET_USER_REF) + 1;

        Set<Long> candidateUserIds = new LinkedHashSet<>();
        candidateUserIds.add(showcase.getTargetUserId());
        Stream.concat(
                partnerUserIdsBySceneId.values().stream().flatMap(List::stream),
                scenes.stream().flatMap(scene -> bubbleUserIds(sceneLinesBySceneId.get(scene.getId())).stream())
        ).forEach(candidateUserIds::add);

        Map<Long, Long> userRefByUserId = new HashMap<>();
        for (Long candidateUserId : candidateUserIds) {
            Long userRef = storedRefByUserId.get(candidateUserId);
            if (userRef == null) {
                userRef = candidateUserId.equals(showcase.getTargetUserId()) ? TARGET_USER_REF : nextRef++;
                showcaseUserRefRepository.save(ShowcaseUserRef.create(showcase.getId(), candidateUserId, userRef));
            }
            userRefByUserId.put(candidateUserId, userRef);
        }

        return userRefByUserId.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private List<Long> bubbleUserIds(List<SceneLine> sceneLines) {
        return sceneLines.stream()
                .filter(SceneBubbleLine.class::isInstance)
                .map(line -> ((SceneBubbleLine) line).userId())
                .toList();
    }

    private Set<Long> displayedUserIds(Map<Long, Long> userRefByUserId, List<Scene> scenes) {
        Set<Long> userIds = new LinkedHashSet<>(userRefByUserId.keySet());

        scenes.stream()
                .flatMap(scene -> Stream.of(scene.getNarration(), scene.getMind(), scene.getLines()))
                .flatMap(text -> sceneNameRenderer.userIds(text).stream())
                .sorted()
                .forEach(userIds::add);

        return userIds;
    }

    private Map<Long, String> maskedNameByUserId(Long showcaseId, Map<Long, Long> userRefByUserId, Set<Long> displayedUserIds, Map<Long, User> userById) {
        List<String> familyNames = new ArrayList<>(PSEUDONYM_FAMILY_NAMES);
        Collections.shuffle(familyNames, new Random(showcaseId));

        Map<Long, String> maskedNameByUserId = new HashMap<>();
        long nextIndex = userRefByUserId.values().stream().mapToLong(Long::longValue).max().orElse(0);

        for (Long userId : displayedUserIds) {
            if (!userById.containsKey(userId)) {
                continue;
            }

            Long userRef = userRefByUserId.get(userId);
            long index = userRef != null ? userRef - 1 : nextIndex++;
            maskedNameByUserId.put(userId, familyNames.get((int) (index % familyNames.size())) + MASKED_GIVEN_NAME);
        }

        return maskedNameByUserId;
    }

    private ShowcaseSceneResult toSceneResult(Scene scene,
                                              List<Long> partnerUserIds,
                                              List<SceneLine> sceneLines,
                                              Map<Long, Long> userRefByUserId,
                                              Map<Long, String> nameByUserId) {
        OffsetDateTime startsAt = KstTimes.toKstOffsetDateTime(scene.getStartsAt());
        OffsetDateTime endsAt = KstTimes.toKstOffsetDateTime(scene.getEndsAt());
        List<Long> with = toUserRefs(partnerUserIds, userRefByUserId);

        return switch (scene.getType()) {
            case ACTION -> new ShowcaseActionSceneResult(
                    scene.getId(),
                    "action",
                    startsAt,
                    endsAt,
                    scene.getPlace(),
                    scene.getPlaceCode(),
                    with,
                    sceneNameRenderer.render(scene.getNarration(), nameByUserId),
                    sceneNameRenderer.render(scene.getMind(), nameByUserId),
                    scene.getLines() == null ? null : toLineResults(sceneLines, userRefByUserId, nameByUserId)
            );
            case DIALOGUE -> new ShowcaseDialogueSceneResult(
                    scene.getId(),
                    "dialogue",
                    startsAt,
                    endsAt,
                    scene.getPlace(),
                    scene.getPlaceCode(),
                    with,
                    toLineResults(sceneLines, userRefByUserId, nameByUserId)
            );
            case MOVE -> new ShowcaseMoveSceneResult(
                    scene.getId(),
                    "move",
                    startsAt,
                    endsAt,
                    scene.getFromPlace(),
                    scene.getFromPlaceCode(),
                    scene.getPlace(),
                    scene.getPlaceCode(),
                    with,
                    scene.getTravelMode(),
                    scene.getMapVersion(),
                    sceneNameRenderer.render(scene.getNarration(), nameByUserId),
                    sceneNameRenderer.render(scene.getMind(), nameByUserId)
            );
        };
    }

    private List<Long> toUserRefs(List<Long> userIds, Map<Long, Long> userRefByUserId) {
        if (userIds == null) {
            return List.of();
        }

        return userIds.stream()
                .map(userRefByUserId::get)
                .toList();
    }

    private List<ShowcaseLineResult> toLineResults(List<SceneLine> sceneLines, Map<Long, Long> userRefByUserId, Map<Long, String> nameByUserId) {
        return sceneLines.stream()
                .map(line -> sceneNameRenderer.render(line, nameByUserId))
                .map(line -> toLineResult(line, userRefByUserId))
                .toList();
    }

    private ShowcaseLineResult toLineResult(SceneLine line, Map<Long, Long> userRefByUserId) {
        return switch (line) {
            case SceneNarrationLine narration -> new ShowcaseNarrationLineResult(
                    narration.t(),
                    narration.text(),
                    narration.occursAt(),
                    narration.speaker()
            );
            case SceneBubbleLine bubble -> new ShowcaseBubbleLineResult(
                    bubble.t(),
                    userRefByUserId.get(bubble.userId()),
                    bubble.action(),
                    bubble.text(),
                    bubble.occursAt()
            );
        };
    }

    private List<SceneLine> toSceneLines(Scene scene) {
        return parseLines(scene).stream()
                .map(SceneLine::from)
                .toList();
    }

    private List<StoredSceneLine> parseLines(Scene scene) {
        if (scene.getLines() == null) {
            return List.of();
        }

        try {
            return objectMapper.readValue(scene.getLines(), new TypeReference<List<StoredSceneLine>>() {
            });
        } catch (JacksonException e) {
            WarnLog.log(log, "씬 대사 파싱에 실패해 빈 목록으로 대체합니다.", e, field("sceneId", scene.getId()));
            return List.of();
        }
    }

    private List<ShowcaseUserInfoResult> toUserInfoResults(Map<Long, Long> userRefByUserId, Map<Long, User> userById, Map<Long, String> nameByUserId) {
        List<ShowcaseUserInfoResult> userInfos = new ArrayList<>();

        userRefByUserId.forEach((userId, userRef) -> {
            User user = userById.get(userId);

            userInfos.add(new ShowcaseUserInfoResult(
                    userRef,
                    nameByUserId.get(userId),
                    user.getGender(),
                    user.shortOrganization()
            ));
        });

        return userInfos;
    }

    private ShowcaseUserCountsResult toUserCountsResult(Long viewerUserId) {
        User viewer = userRepository.findById(viewerUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Instant now = Instant.now();

        return new ShowcaseUserCountsResult(
                userRepository.countActiveWithEntitlement(EntitlementReader.SIMULATION_ACCESS, now)
                        + TOTAL_USER_COUNT_OFFSET,
                userRepository.countActiveWithEntitlementByOrganizationHash(viewer.getOrganizationHash(), EntitlementReader.SIMULATION_ACCESS, now)
                        + SAME_ORGANIZATION_USER_COUNT_OFFSET,
                viewer.shortOrganization()
        );
    }

    private WorldActorProfile worldProfile(Long userId, Map<Long, String> nameByUserId, Map<Long, User> userById) {
        User user = userId == null ? null : userById.get(userId);
        if (user == null) {
            return WorldService.anonymousProfile();
        }

        return new WorldActorProfile(
                nameByUserId.getOrDefault(userId, WorldService.ANONYMOUS_DISPLAY_NAME),
                WorldService.isWithdrawn(user) ? null : WorldService.genderOf(user)
        );
    }

    private String toShowcaseWorldRef(String ref, Long showcaseId, Map<Long, Long> userRefByUserId) {
        Long userId = WorldService.toUserId(ref);
        if (userId == null) {
            return ref;
        }

        Long userRef = userRefByUserId.get(userId);
        return WorldService.toUserRef(userRef != null ? userRef : showcaseOutsiderRefs.refOf(showcaseId, userId));
    }

    private record ShowcaseState(Showcase showcase,
                                 LocalDate date,
                                 List<Scene> scenes,
                                 Map<Long, List<Long>> partnerUserIdsBySceneId,
                                 Map<Long, List<SceneLine>> sceneLinesBySceneId,
                                 Map<Long, Long> userRefByUserId) {
    }
}
