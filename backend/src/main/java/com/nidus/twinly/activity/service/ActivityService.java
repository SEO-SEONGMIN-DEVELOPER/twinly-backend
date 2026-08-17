package com.nidus.twinly.activity.service;

import com.nidus.twinly.activity.dto.result.*;
import com.nidus.twinly.activity.entity.Question;
import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.entity.ScenePartner;
import com.nidus.twinly.activity.repository.QuestionRepository;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.common.scene.SceneLine;
import com.nidus.twinly.common.scene.StoredSceneLine;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.common.photo.ProfilePhotoInfo;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import com.nidus.twinly.user.entity.Photo;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PhotoRepository;
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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityService {

    private final SceneRepository sceneRepository;
    private final ScenePartnerRepository scenePartnerRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final PhotoRepository photoRepository;
    private final CloudFrontService cloudFrontService;
    private final CurrentSeasonReader currentSeasonReader;
    private final ObjectMapper objectMapper;

    public ActivityResult activity(Long userId, LocalDate date) {
        List<Scene> scenes = sceneRepository.findAllByUserIdAndDate(userId, date);
        List<Long> sceneIds = scenes.stream().map(Scene::getId).toList();

        List<ScenePartner> scenePartners = scenePartnerRepository.findAllBySceneIdIn(sceneIds);
        Map<Long, List<Long>> partnerUserIdsBySceneId = scenePartners.stream()
                .collect(Collectors.groupingBy(ScenePartner::getSceneId, Collectors.mapping(ScenePartner::getUserId, Collectors.toList())));

        List<Long> userInfoUserIds = Stream.concat(Stream.of(userId), scenePartners.stream().map(ScenePartner::getUserId))
                .distinct()
                .toList();
        Map<Long, User> userById = userRepository.findAllById(userInfoUserIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<ActivitySceneResult> sceneResults = scenes.stream()
                .map(scene -> toSceneResult(scene, partnerUserIdsBySceneId.get(scene.getId())))
                .toList();

        List<Question> questions = questionRepository.findAllByUserIdAndDate(userId, date);
        List<ActivityQuestionResult> questionResults = questions.stream()
                .map(this::toQuestionResult)
                .toList();

        return new ActivityResult(
                userId,
                currentSeasonReader.read().getId(),
                date,
                scenes.isEmpty() ? null : scenes.get(0).getVersion(),
                Instant.now(),
                sceneResults,
                questionResults,
                toUserInfoResults(userInfoUserIds, userById)
        );
    }

    private ActivitySceneResult toSceneResult(Scene scene, List<Long> with) {
        OffsetDateTime startsAt = KstTimes.toKstOffsetDateTime(scene.getStartsAt());
        OffsetDateTime endsAt = KstTimes.toKstOffsetDateTime(scene.getEndsAt());

        return switch (scene.getType()) {
            case ACTION -> new ActivityActionSceneResult(
                    scene.getId(),
                    "action",
                    startsAt,
                    endsAt,
                    scene.getPlace(),
                    with,
                    scene.getNarration(),
                    scene.getMind()
            );
            case DIALOGUE -> new ActivityDialogueSceneResult(
                    scene.getId(),
                    "dialogue",
                    startsAt,
                    endsAt,
                    scene.getPlace(),
                    with,
                    toSceneLines(scene)
            );
        };
    }

    private List<ActivityUserInfoResult> toUserInfoResults(List<Long> userIds, Map<Long, User> userById) {
        List<Long> visibleUserIds = userIds.stream()
                .filter(userId -> !userById.get(userId).isWithdrawn())
                .toList();

        Map<Long, ProfilePhotoInfo> profilePhotoByUserId = photoRepository.findAllByUserIdInAndType(visibleUserIds, PhotoType.PROFILE).stream()
                .collect(Collectors.toMap(Photo::getUserId, photo -> new ProfilePhotoInfo(photo.getKey(), cloudFrontService.getSignedUrl(photo.getKey()), photo.position())));

        return userIds.stream()
                .map(userId -> new ActivityUserInfoResult(
                        userId,
                        userById.get(userId).displayGivenName(),
                        profilePhotoByUserId.get(userId)))
                .toList();
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

    private ActivityQuestionResult toQuestionResult(Question question) {
        OffsetDateTime time = KstTimes.toKstOffsetDateTime(question.getTime());

        return new ActivityQuestionResult(
                question.getId(),
                question.getType().name(),
                time,
                question.getText(),
                question.getOptions()
        );
    }
}