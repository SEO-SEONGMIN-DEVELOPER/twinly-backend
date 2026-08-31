package com.nidus.twinly.simulation.service;

import com.nidus.twinly.activity.entity.Question;
import com.nidus.twinly.activity.entity.QuestionPartner;
import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.entity.ScenePartner;
import com.nidus.twinly.activity.repository.QuestionPartnerRepository;
import com.nidus.twinly.activity.repository.QuestionRepository;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.chat.opener.ChatRoomOpener;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.chat.repository.ChatRoomOpeningRepository;
import com.nidus.twinly.common.scene.StoredSceneBubbleLine;
import com.nidus.twinly.common.scene.StoredSceneLine;
import com.nidus.twinly.common.scene.StoredSceneNarrationLine;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.notification.writer.AppNotificationFeedWriter;
import com.nidus.twinly.people.repository.EncounterRepository;
import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.relationship.domain.RelationshipType;
import com.nidus.twinly.relationship.entity.Relationship;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.simulation.dto.command.*;
import com.nidus.twinly.simulation.dto.result.SimulationPersonaResult;
import com.nidus.twinly.user.entity.PersonaElement;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PersonaElementRepository;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SimulationService {

    private static final String VERSION_PREFIX = "v";
    private static final String FIRST_VERSION = "v1";

    private final SceneRepository sceneRepository;
    private final ScenePartnerRepository scenePartnerRepository;
    private final QuestionRepository questionRepository;
    private final QuestionPartnerRepository questionPartnerRepository;
    private final RelationshipRepository relationshipRepository;
    private final EncounterRepository encounterRepository;
    private final ChatRoomOpener chatRoomOpener;
    private final ChatRoomOpeningRepository chatRoomOpeningRepository;
    private final AppNotificationFeedWriter appNotificationFeedWriter;
    private final UserRepository userRepository;
    private final PersonaElementRepository personaElementRepository;
    private final EntitlementReader entitlementReader;
    private final ObjectMapper objectMapper;

    public void simulations(Long userId, SimulationsCommand command) {
        if (!userId.equals(command.userId())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        LocalDate date = command.date();
        List<Scene> previousScenes = sceneRepository.findAllByUserIdAndDate(userId, date);
        String version = nextVersion(previousScenes);

        deletePrevious(userId, date, previousScenes);

        saveScenes(userId, date, version, command.scenes());
        saveQuestions(userId, date, version, command.questions());
        saveRelationships(userId, date, version, command.relationships());
    }

    private String nextVersion(List<Scene> previousScenes) {
        return previousScenes.stream()
                .map(Scene::getVersion)
                .findFirst()
                .map(this::increase)
                .orElse(FIRST_VERSION);
    }

    private String increase(String version) {
        if (version == null || !version.startsWith(VERSION_PREFIX)) {
            return FIRST_VERSION;
        }

        try {
            return VERSION_PREFIX + (Integer.parseInt(version.substring(VERSION_PREFIX.length())) + 1);
        } catch (NumberFormatException e) {
            return FIRST_VERSION;
        }
    }

    private void deletePrevious(Long userId, LocalDate date, List<Scene> previousScenes) {
        if (!previousScenes.isEmpty()) {
            scenePartnerRepository.deleteAllBySceneIdIn(previousScenes.stream().map(Scene::getId).toList());
            sceneRepository.deleteAll(previousScenes);
        }

        List<Question> previousQuestions = questionRepository.findAllByUserIdAndDate(userId, date);
        if (!previousQuestions.isEmpty()) {
            questionPartnerRepository.deleteAllByQuestionIdIn(previousQuestions.stream().map(Question::getId).toList());
            questionRepository.deleteAll(previousQuestions);
        }

        relationshipRepository.deleteAllByUserIdAndDate(userId, date);
        relationshipRepository.flush();
    }

    private void saveScenes(Long userId, LocalDate date, String version, List<SimulationsSceneCommand> commands) {
        List<Scene> scenes = sceneRepository.saveAll(commands.stream()
                .map(command -> toScene(userId, date, version, command))
                .toList());

        scenePartnerRepository.saveAll(IntStream.range(0, scenes.size())
                .boxed()
                .flatMap(index -> partnerUserIds(commands.get(index)).stream()
                        .map(partnerUserId -> ScenePartner.create(scenes.get(index).getId(), partnerUserId)))
                .toList());
    }

    private Scene toScene(Long userId, LocalDate date, String version, SimulationsSceneCommand command) {
        return switch (command) {
            case SimulationsActionSceneCommand action -> Scene.createAction(
                    userId,
                    date,
                    version,
                    action.place(),
                    action.start(),
                    action.end(),
                    action.narration(),
                    action.mind()
            );
            case SimulationsDialogueSceneCommand dialogue -> Scene.createDialogue(
                    userId,
                    date,
                    version,
                    dialogue.place(),
                    dialogue.start(),
                    dialogue.end(),
                    writeLines(dialogue.lines())
            );
        };
    }

    private String writeLines(List<SimulationsLineCommand> commands) {
        return objectMapper.writeValueAsString(commands.stream()
                .map(this::toSceneLine)
                .toList());
    }

    private StoredSceneLine toSceneLine(SimulationsLineCommand command) {
        return switch (command) {
            case SimulationsNarrationLineCommand narration -> new StoredSceneNarrationLine(
                    narration.t(),
                    narration.text(),
                    narration.occursAt()
            );
            case SimulationsBubbleLineCommand bubble -> new StoredSceneBubbleLine(
                    bubble.t(),
                    bubble.userId(),
                    bubble.action(),
                    bubble.text(),
                    bubble.occursAt()
            );
        };
    }

    private List<Long> partnerUserIds(SimulationsSceneCommand command) {
        List<Long> with = switch (command) {
            case SimulationsActionSceneCommand action -> action.with();
            case SimulationsDialogueSceneCommand dialogue -> dialogue.with();
        };

        return distinct(with);
    }

    private void saveQuestions(Long userId, LocalDate date, String version, List<SimulationsQuestionCommand> commands) {
        List<Question> questions = questionRepository.saveAll(commands.stream()
                .map(command -> Question.create(userId, date, version, command.time(), command.qtype(), command.text(), command.options()))
                .toList());

        questionPartnerRepository.saveAll(IntStream.range(0, questions.size())
                .boxed()
                .flatMap(index -> distinct(commands.get(index).partnerId()).stream()
                        .map(partnerUserId -> QuestionPartner.create(questions.get(index).getId(), partnerUserId)))
                .toList());
    }

    private void saveRelationships(Long userId, LocalDate date, String version, List<SimulationsRelationshipCommand> commands) {
        List<Long> becameFriendPartnerUserIds = becameFriendPartnerUserIds(userId, date, commands);

        relationshipRepository.saveAll(commands.stream()
                .map(command -> Relationship.create(userId, date, version, command.partnerId(), command.rapport(),
                        command.partnerModel(), command.updateTime()))
                .toList());

        commands.stream()
                .map(SimulationsRelationshipCommand::partnerId)
                .distinct()
                .forEach(partnerUserId -> encounterRepository.upsert(
                        Math.min(userId, partnerUserId), Math.max(userId, partnerUserId)));

        becameFriendPartnerUserIds.forEach(partnerUserId -> appNotificationFeedWriter.writeFriend(userId, partnerUserId, date));

        commands.forEach(command -> openChatRoom(userId, command.partnerId(), command.rapport(), command.updateTime()));
    }

    private List<Long> becameFriendPartnerUserIds(Long userId, LocalDate date, List<SimulationsRelationshipCommand> commands) {
        return commands.stream()
                .filter(command -> becameFriend(userId, date, command))
                .map(SimulationsRelationshipCommand::partnerId)
                .distinct()
                .toList();
    }

    private boolean becameFriend(Long userId, LocalDate date, SimulationsRelationshipCommand command) {
        RelationshipType previous = relationshipRepository
                .findLatestByUserIdAndPartnerUserIdBeforeDate(userId, command.partnerId(), date)
                .map(relationship -> RelationshipType.fromIntimacy(relationship.getIntimacy()))
                .orElse(RelationshipType.ACQUAINTANCE);

        return previous == RelationshipType.ACQUAINTANCE
                && RelationshipType.fromIntimacy(command.rapport()) != RelationshipType.ACQUAINTANCE;
    }

    private void openChatRoom(Long userId, Long partnerUserId, Integer intimacy, LocalDateTime updateTime) {
        if (RelationshipType.fromIntimacy(intimacy) != RelationshipType.BEST_FRIEND) {
            return;
        }

        Instant scheduledAt = KstTimes.toInstant(updateTime);

        if (scheduledAt.isAfter(Instant.now())) {
            chatRoomOpeningRepository.upsert(Math.min(userId, partnerUserId), Math.max(userId, partnerUserId),
                    scheduledAt);
            return;
        }

        try {
            chatRoomOpener.open(userId, partnerUserId);
        } catch (DataIntegrityViolationException e) {
            log.info("상대 쪽에서 채팅방을 먼저 열어 개설을 건너뜁니다. userId={}, partnerUserId={}", userId, partnerUserId);
        }
    }

    private List<Long> distinct(List<Long> userIds) {
        if (userIds == null) {
            return List.of();
        }

        return userIds.stream().distinct().toList();
    }

    @Transactional(readOnly = true)
    public SimulationPersonaResult persona(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.isWithdrawn()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (!entitlementReader.hasSimulationAccess(userId)) {
            throw new BusinessException(ErrorCode.SIMULATION_ACCESS_REQUIRED);
        }

        Map<PersonaDimension, List<String>> personaElements = personaElementRepository.findAllByUserIdOrderByIdAsc(userId).stream()
                .collect(Collectors.groupingBy(
                        PersonaElement::getDimension,
                        LinkedHashMap::new,
                        Collectors.mapping(PersonaElement::getExplanation, Collectors.toList())
                ));

        return new SimulationPersonaResult(
                user.getId(),
                user.getFamilyName(),
                user.getGivenName(),
                user.getGender(),
                user.getOrganization(),
                user.getAffiliation(),
                birthDate(user),
                personaElements
        );
    }

    private LocalDate birthDate(User user) {
        return user.getBirthDate() == null ? null : LocalDate.parse(user.getBirthDate());
    }
}
