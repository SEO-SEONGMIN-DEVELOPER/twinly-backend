package com.nidus.twinly.activity.service;

import com.nidus.twinly.activity.dto.result.*;
import com.nidus.twinly.activity.entity.Question;
import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.entity.ScenePartner;
import com.nidus.twinly.activity.repository.QuestionRepository;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String CURRENT_VERSION = "v1";

    @Value("${app.current-season-id}")
    private Long currentSeasonId;

    private final SceneRepository sceneRepository;
    private final ScenePartnerRepository scenePartnerRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ActivityResult activity(Long userId, LocalDate date) {
        List<Scene> scenes = sceneRepository.findAllByUserIdAndDate(userId, date);
        List<Long> sceneIds = scenes.stream().map(Scene::getId).toList();

        List<ScenePartner> scenePartners = scenePartnerRepository.findAllBySceneIdIn(sceneIds);
        Map<Long, List<Long>> partnerUserIdsBySceneId = scenePartners.stream()
                .collect(Collectors.groupingBy(ScenePartner::getSceneId, Collectors.mapping(ScenePartner::getUserId, Collectors.toList())));

        List<Long> allPartnerUserIds = scenePartners.stream().map(ScenePartner::getUserId).distinct().toList();
        Map<Long, User> partnerUserById = userRepository.findAllById(allPartnerUserIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<ActivitySceneResult> sceneResults = scenes.stream()
                .map(scene -> toSceneResult(scene, partnerUserIdsBySceneId.getOrDefault(scene.getId(), List.of()), partnerUserById))
                .toList();

        List<Question> questions = questionRepository.findAllByUserIdAndDate(userId, date);
        List<ActivityQuestionResult> questionResults = questions.stream()
                .map(this::toQuestionResult)
                .toList();

        return new ActivityResult(
                userId,
                currentSeasonId,
                date,
                CURRENT_VERSION,
                Instant.now(),
                sceneResults,
                questionResults
        );
    }

    private ActivitySceneResult toSceneResult(Scene scene, List<Long> partnerUserIds, Map<Long, User> partnerUserById) {
        List<ActivitySpeakerResult> with = partnerUserIds.stream()
                .map(partnerUserId -> toSpeakerResult(partnerUserId, partnerUserById))
                .toList();

        Instant startsAt = toKstInstant(scene.getStartsAt());
        Instant endsAt = toKstInstant(scene.getEndsAt());

        return switch (scene.getType()) {
            case ACTION -> new ActivityActionSceneResult(
                    "action",
                    startsAt,
                    endsAt,
                    scene.getPlace(),
                    with,
                    scene.getNarration(),
                    scene.getMind()
            );
            case DIALOGUE -> new ActivityDialogueSceneResult(
                    "dialogue",
                    startsAt,
                    endsAt,
                    scene.getPlace(),
                    with,
                    parseDialogues(scene.getDialogues())
            );
        };
    }

    private ActivitySpeakerResult toSpeakerResult(Long partnerUserId, Map<Long, User> partnerUserById) {
        User partner = partnerUserById.get(partnerUserId);
        String userName = partner != null ? partner.getFamilyName() + partner.getGivenName() : null;
        return new ActivitySpeakerResult(partnerUserId, userName);
    }

    private Instant toKstInstant(LocalDateTime localDateTime) {
        return localDateTime.atZone(KST).toInstant();
    }

    private List<ActivityDialogueResult> parseDialogues(String dialoguesJson) {
        if (dialoguesJson == null) {
            return List.of();
        }

        return objectMapper.readValue(dialoguesJson, new TypeReference<List<ActivityDialogueResult>>() {
        });
    }

    private ActivityQuestionResult toQuestionResult(Question question) {
        return new ActivityQuestionResult(
                question.getId(),
                question.getType().name(),
                question.getTime(),
                question.getText(),
                question.getOptions()
        );
    }
}