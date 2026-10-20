package com.nidus.twinly.admin.reader;

import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.entity.ScenePartner;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.admin.dto.result.AdminSceneResult;
import com.nidus.twinly.common.logging.WarnLog;
import com.nidus.twinly.common.scene.SceneNameRenderer;
import com.nidus.twinly.common.scene.StoredSceneBubbleLine;
import com.nidus.twinly.common.scene.StoredSceneLine;
import com.nidus.twinly.common.scene.StoredSceneNarrationLine;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSceneReader {

    private final SceneRepository sceneRepository;
    private final ScenePartnerRepository scenePartnerRepository;
    private final UserRepository userRepository;
    private final SceneNameRenderer sceneNameRenderer;
    private final ObjectMapper objectMapper;

    public List<AdminSceneResult> scenes(Long userId, LocalDate date) {
        List<Scene> scenes = sceneRepository.findAllByUserIdAndDateOrderByStartsAtAsc(userId, date);

        if (scenes.isEmpty()) {
            return List.of();
        }

        Map<Long, List<Long>> partnerIdsBySceneId = scenePartnerRepository.findAllBySceneIdIn(scenes.stream().map(Scene::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(ScenePartner::getSceneId, Collectors.mapping(ScenePartner::getUserId, Collectors.toList())));
        Map<Long, List<StoredSceneLine>> linesBySceneId = scenes.stream()
                .collect(Collectors.toMap(Scene::getId, this::parseLines));
        Map<Long, String> nicknameById = nicknames(scenes, partnerIdsBySceneId, linesBySceneId);

        return scenes.stream()
                .map(scene -> toResult(scene, partnerIdsBySceneId.getOrDefault(scene.getId(), List.of()),
                        linesBySceneId.get(scene.getId()), nicknameById))
                .toList();
    }

    private AdminSceneResult toResult(Scene scene, List<Long> partnerIds, List<StoredSceneLine> lines, Map<Long, String> nicknameById) {
        return new AdminSceneResult(
                scene.getId(),
                scene.getType().name(),
                KstTimes.toKstOffsetDateTime(scene.getStartsAt()),
                KstTimes.toKstOffsetDateTime(scene.getEndsAt()),
                scene.getPlace(),
                scene.getFromPlace(),
                scene.getTravelMode(),
                sceneNameRenderer.render(scene.getNarration(), nicknameById),
                sceneNameRenderer.render(scene.getMind(), nicknameById),
                partnerIds.stream().map(id -> nicknameById.getOrDefault(id, User.WITHDRAWN_NAME)).toList(),
                lines.stream().map(line -> toLine(line, nicknameById)).toList()
        );
    }

    private AdminSceneResult.Line toLine(StoredSceneLine line, Map<Long, String> nicknameById) {
        return switch (line) {
            case StoredSceneNarrationLine narration -> new AdminSceneResult.Line(
                    null, null, sceneNameRenderer.render(narration.text(), nicknameById));
            case StoredSceneBubbleLine bubble -> new AdminSceneResult.Line(
                    nicknameById.getOrDefault(bubble.userId(), User.WITHDRAWN_NAME),
                    sceneNameRenderer.render(bubble.action(), nicknameById),
                    sceneNameRenderer.render(bubble.text(), nicknameById));
        };
    }

    private Map<Long, String> nicknames(List<Scene> scenes, Map<Long, List<Long>> partnerIdsBySceneId,
                                        Map<Long, List<StoredSceneLine>> linesBySceneId) {
        Set<Long> userIds = new HashSet<>();

        scenes.forEach(scene -> {
            userIds.add(scene.getUserId());
            userIds.addAll(sceneNameRenderer.userIds(scene.getNarration()));
            userIds.addAll(sceneNameRenderer.userIds(scene.getMind()));
        });
        partnerIdsBySceneId.values().forEach(userIds::addAll);
        linesBySceneId.values().forEach(lines -> lines.forEach(line -> {
            switch (line) {
                case StoredSceneNarrationLine narration -> userIds.addAll(sceneNameRenderer.userIds(narration.text()));
                case StoredSceneBubbleLine bubble -> {
                    if (bubble.userId() != null) {
                        userIds.add(bubble.userId());
                    }
                    userIds.addAll(sceneNameRenderer.userIds(bubble.action()));
                    userIds.addAll(sceneNameRenderer.userIds(bubble.text()));
                }
            }
        }));

        Map<Long, String> nicknameById = new HashMap<>();
        userRepository.findAllById(userIds).forEach(user -> {
            if (user.displayNickname() != null) {
                nicknameById.put(user.getId(), user.displayNickname());
            }
        });

        return nicknameById;
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
}
