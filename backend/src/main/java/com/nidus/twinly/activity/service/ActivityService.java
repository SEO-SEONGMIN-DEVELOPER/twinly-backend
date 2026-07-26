package com.nidus.twinly.activity.service;

import com.nidus.twinly.activity.dto.result.*;
import com.nidus.twinly.activity.entity.Question;
import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.entity.ScenePartner;
import com.nidus.twinly.activity.repository.QuestionRepository;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActivityService {

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
                scenes.isEmpty() ? null : scenes.get(0).getVersion(),
                Instant.now(),
                sceneResults,
                questionResults
        );
    }

    private ActivitySceneResult toSceneResult(Scene scene, List<Long> partnerUserIds, Map<Long, User> partnerUserById) {
        List<ActivitySpeakerResult> with = partnerUserIds.stream()
                .map(partnerUserId -> toSpeakerResult(partnerUserId, partnerUserById))
                .toList();

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
                    parseLines(scene.getLines())
            );
        };
    }

    private ActivitySpeakerResult toSpeakerResult(Long partnerUserId, Map<Long, User> partnerUserById) {
        User partner = partnerUserById.get(partnerUserId);
        String userName = partner != null ? partner.getFamilyName() + partner.getGivenName() : null;
        return new ActivitySpeakerResult(partnerUserId, userName);
    }

    private List<ActivityLineResult> parseLines(String linesJson) {
        if (linesJson == null) {
            return List.of();
        }

        return objectMapper.readValue(linesJson, new TypeReference<List<ActivityLineResult>>() {
        });
    }

    private ActivityQuestionResult toQuestionResult(Question question) {
        OffsetDateTime time = KstTimes.toKstOffsetDateTime(question.getDate().atTime(question.getTime()));

        return new ActivityQuestionResult(
                question.getId(),
                question.getType().name(),
                time,
                question.getText(),
                question.getOptions()
        );
    }
}