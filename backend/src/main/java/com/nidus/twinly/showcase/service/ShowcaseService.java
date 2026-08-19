package com.nidus.twinly.showcase.service;

import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.entity.ScenePartner;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.common.scene.SceneBubbleLine;
import com.nidus.twinly.common.scene.SceneLine;
import com.nidus.twinly.common.scene.SceneNameRenderer;
import com.nidus.twinly.common.scene.SceneNarrationLine;
import com.nidus.twinly.common.scene.StoredSceneLine;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import com.nidus.twinly.showcase.dto.result.*;
import com.nidus.twinly.showcase.entity.Showcase;
import com.nidus.twinly.showcase.repository.ShowcaseRepository;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShowcaseService {

    private static final String UNIVERSITY_SUFFIX = "대학교";
    private static final String SCHOOL_SUFFIX = "학교";
    private static final long TARGET_USER_REF = 1L;

    private final ShowcaseRepository showcaseRepository;
    private final SceneRepository sceneRepository;
    private final ScenePartnerRepository scenePartnerRepository;
    private final UserRepository userRepository;
    private final CurrentSeasonReader currentSeasonReader;
    private final SceneNameRenderer sceneNameRenderer;
    private final ObjectMapper objectMapper;

    @Transactional
    public ShowcaseTodayResult today(Long userId) {
        LocalDate date = KstTimes.today();
        Showcase showcase = showcaseRepository.findByViewerUserIdAndDate(userId, date)
                .orElseGet(() -> showcaseRepository.save(Showcase.create(userId, pickTargetUserId(userId, date), date)));

        List<Scene> scenes = sceneRepository.findAllByUserIdAndDateOrderByStartsAtAsc(showcase.getTargetUserId(), date);
        Map<Long, List<Long>> partnerUserIdsBySceneId = partnerUserIdsBySceneId(scenes);
        Map<Long, List<SceneLine>> sceneLinesBySceneId = sceneLinesBySceneId(scenes);

        Map<Long, Long> userRefByUserId = userRefByUserId(showcase.getTargetUserId(), scenes, partnerUserIdsBySceneId, sceneLinesBySceneId);
        Map<Long, User> userById = userRepository.findAllById(userRefByUserId.keySet()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, String> maskedNameByUserId = maskedNameByUserId(scenes);

        return new ShowcaseTodayResult(
                showcase.getId(),
                TARGET_USER_REF,
                date,
                Instant.now(),
                scenes.stream()
                        .map(scene -> toSceneResult(scene, partnerUserIdsBySceneId.get(scene.getId()), sceneLinesBySceneId.get(scene.getId()), userRefByUserId, maskedNameByUserId))
                        .toList(),
                toUserInfoResults(userRefByUserId, userById),
                toUserCountsResult(userId)
        );
    }

    private Long pickTargetUserId(Long viewerUserId, LocalDate date) {
        List<Long> candidateUserIds = showcaseRepository.findAllTargetCandidateUserIds(
                viewerUserId, currentSeasonReader.read().getId(), date);

        if (candidateUserIds.isEmpty()) {
            throw new BusinessException(ErrorCode.SHOWCASE_TARGET_NOT_FOUND);
        }

        return candidateUserIds.get(ThreadLocalRandom.current().nextInt(candidateUserIds.size()));
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

    private Map<Long, Long> userRefByUserId(Long targetUserId,
                                            List<Scene> scenes,
                                            Map<Long, List<Long>> partnerUserIdsBySceneId,
                                            Map<Long, List<SceneLine>> sceneLinesBySceneId) {
        Map<Long, Long> userRefByUserId = new LinkedHashMap<>();
        userRefByUserId.put(targetUserId, TARGET_USER_REF);

        Stream.concat(
                partnerUserIdsBySceneId.values().stream().flatMap(List::stream),
                scenes.stream().flatMap(scene -> bubbleUserIds(sceneLinesBySceneId.get(scene.getId())).stream())
        ).forEach(userId -> userRefByUserId.putIfAbsent(userId, (long) (userRefByUserId.size() + 1)));

        return userRefByUserId;
    }

    private List<Long> bubbleUserIds(List<SceneLine> sceneLines) {
        return sceneLines.stream()
                .filter(SceneBubbleLine.class::isInstance)
                .map(line -> ((SceneBubbleLine) line).userId())
                .toList();
    }

    private Map<Long, String> maskedNameByUserId(List<Scene> scenes) {
        Set<Long> userIds = scenes.stream()
                .flatMap(scene -> Stream.of(scene.getNarration(), scene.getMind(), scene.getLines()))
                .flatMap(text -> sceneNameRenderer.userIds(text).stream())
                .collect(Collectors.toSet());

        if (userIds.isEmpty()) {
            return Map.of();
        }

        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::displayMaskedName));
    }

    private ShowcaseSceneResult toSceneResult(Scene scene,
                                              List<Long> partnerUserIds,
                                              List<SceneLine> sceneLines,
                                              Map<Long, Long> userRefByUserId,
                                              Map<Long, String> maskedNameByUserId) {
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
                    with,
                    sceneNameRenderer.render(scene.getNarration(), maskedNameByUserId),
                    sceneNameRenderer.render(scene.getMind(), maskedNameByUserId)
            );
            case DIALOGUE -> new ShowcaseDialogueSceneResult(
                    scene.getId(),
                    "dialogue",
                    startsAt,
                    endsAt,
                    scene.getPlace(),
                    with,
                    toLineResults(sceneLines, userRefByUserId, maskedNameByUserId)
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

    private List<ShowcaseLineResult> toLineResults(List<SceneLine> sceneLines, Map<Long, Long> userRefByUserId, Map<Long, String> maskedNameByUserId) {
        return sceneLines.stream()
                .map(line -> sceneNameRenderer.render(line, maskedNameByUserId))
                .map(line -> toLineResult(line, userRefByUserId))
                .toList();
    }

    private ShowcaseLineResult toLineResult(SceneLine line, Map<Long, Long> userRefByUserId) {
        return switch (line) {
            case SceneNarrationLine narration -> new ShowcaseNarrationLineResult(
                    narration.t(),
                    narration.text(),
                    narration.occursAt()
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
            log.warn("씬 대사 파싱에 실패해 빈 목록으로 대체합니다. sceneId={}", scene.getId(), e);
            return List.of();
        }
    }

    private List<ShowcaseUserInfoResult> toUserInfoResults(Map<Long, Long> userRefByUserId, Map<Long, User> userById) {
        List<ShowcaseUserInfoResult> userInfos = new ArrayList<>();

        userRefByUserId.forEach((userId, userRef) -> {
            User user = userById.get(userId);

            userInfos.add(new ShowcaseUserInfoResult(
                    userRef,
                    user.displayMaskedName(),
                    user.getGender(),
                    toDisplayOrganization(user.getOrganization())
            ));
        });

        return userInfos;
    }

    private String toDisplayOrganization(String organization) {
        if (organization == null || !organization.endsWith(UNIVERSITY_SUFFIX)) {
            return organization;
        }

        return organization.substring(0, organization.length() - SCHOOL_SUFFIX.length());
    }

    private ShowcaseUserCountsResult toUserCountsResult(Long viewerUserId) {
        User viewer = userRepository.findById(viewerUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return new ShowcaseUserCountsResult(
                userRepository.countByDeletedAtIsNull(),
                userRepository.countByDeletedAtIsNullAndOrganizationHash(viewer.getOrganizationHash())
        );
    }
}
