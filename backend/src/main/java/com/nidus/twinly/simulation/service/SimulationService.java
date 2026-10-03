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
import com.nidus.twinly.common.logging.InfoLog;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.chat.repository.ChatRoomOpeningRepository;
import com.nidus.twinly.common.scene.StoredSceneBubbleLine;
import com.nidus.twinly.common.scene.StoredSceneLine;
import com.nidus.twinly.common.scene.StoredSceneNarrationLine;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.notification.domain.AppNotificationScheduleType;
import com.nidus.twinly.notification.repository.AppNotificationScheduleRepository;
import com.nidus.twinly.people.repository.EncounterRepository;
import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.legal.domain.PolicyKind;
import com.nidus.twinly.legal.reader.ConsentReader;
import com.nidus.twinly.purchase.service.PurchaseService;
import com.nidus.twinly.relationship.domain.RelationshipType;
import com.nidus.twinly.relationship.entity.Relationship;
import com.nidus.twinly.relationship.reader.IntimacyReader;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.simulation.dto.command.*;
import com.nidus.twinly.simulation.dto.result.SimulationPersonaIntimacyResult;
import com.nidus.twinly.simulation.dto.result.SimulationPersonaResult;
import com.nidus.twinly.user.entity.PersonaElement;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PersonaElementRepository;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SimulationService {

    private static final String VERSION_PREFIX = "v";
    private static final String FIRST_VERSION = "v1";
    private static final String PLACE_SEPARATOR = ":";
    private static final String PLACE_SEPARATOR_REPLACEMENT = " ";
    private static final int FIRST_MEETING_INTIMACY = 0;
    private static final String FIRST_MEETING_PARTNER_MODEL = "아직 알게된 점이 없습니다.";
    private static final Duration INTIMACY_AS_OF_MARGIN = Duration.ofSeconds(5);

    private final SceneRepository sceneRepository;
    private final ScenePartnerRepository scenePartnerRepository;
    private final QuestionRepository questionRepository;
    private final QuestionPartnerRepository questionPartnerRepository;
    private final RelationshipRepository relationshipRepository;
    private final EncounterRepository encounterRepository;
    private final ChatRoomOpener chatRoomOpener;
    private final ChatRoomOpeningRepository chatRoomOpeningRepository;
    private final AppNotificationScheduleRepository appNotificationScheduleRepository;
    private final UserRepository userRepository;
    private final PersonaElementRepository personaElementRepository;
    private final EntitlementReader entitlementReader;
    private final ConsentReader consentReader;
    private final PurchaseService purchaseService;
    private final ObjectMapper objectMapper;
    private final IntimacyReader intimacyReader;

    public void simulations(Long userId, SimulationsCommand command) {
        if (!userId.equals(command.userId())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (command.intimacyAsOf() != null && command.intimacyAsOf().isAfter(Instant.now())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        validatePartnersExist(command);

        LocalDate date = command.date();
        List<Scene> previousScenes = sceneRepository.findAllByUserIdAndDate(userId, date);
        String version = nextVersion(previousScenes);

        deletePrevious(userId, date, previousScenes);

        saveScenes(userId, date, version, command.scenes());
        Map<Long, LocalDateTime> firstMeetingStartByPartnerUserId = firstMeetingStartByPartnerUserId(userId, date, command.scenes());
        scheduleFirstMeetings(userId, date, firstMeetingStartByPartnerUserId);
        saveQuestions(userId, date, version, command.questions());
        saveRelationships(userId, date, version, command.relationships(), firstMeetingStartByPartnerUserId, command.intimacyAsOf());
    }

    private void validatePartnersExist(SimulationsCommand command) {
        Set<Long> partnerUserIds = Stream.of(
                        command.scenes().stream().flatMap(scene -> partnerUserIds(scene).stream()),
                        command.questions().stream().flatMap(question -> distinct(question.partnerId()).stream()),
                        command.relationships().stream().map(SimulationsRelationshipCommand::partnerId))
                .flatMap(Function.identity())
                .collect(Collectors.toSet());

        if (partnerUserIds.isEmpty()) {
            return;
        }

        if (userRepository.countByIdIn(partnerUserIds) != partnerUserIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
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

        appNotificationScheduleRepository.deleteAllUnsentByUserIdAndSimulationDate(userId, date);
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
                    normalizePlace(action.place()),
                    action.start(),
                    action.end(),
                    action.narration(),
                    action.mind()
            );
            case SimulationsDialogueSceneCommand dialogue -> Scene.createDialogue(
                    userId,
                    date,
                    version,
                    normalizePlace(dialogue.place()),
                    dialogue.start(),
                    dialogue.end(),
                    writeLines(dialogue.lines())
            );
        };
    }

    private String normalizePlace(String place) {
        if (place == null) {
            return null;
        }

        return place.replace(PLACE_SEPARATOR, PLACE_SEPARATOR_REPLACEMENT);
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

    private Map<Long, LocalDateTime> firstMeetingStartByPartnerUserId(Long userId, LocalDate date, List<SimulationsSceneCommand> commands) {
        Map<Long, LocalDateTime> firstDialogueStartByPartnerUserId = firstDialogueStartByPartnerUserId(userId, commands);

        if (firstDialogueStartByPartnerUserId.isEmpty()) {
            return Map.of();
        }

        List<Long> alreadyMetPartnerUserIds = scenePartnerRepository.findPartnerUserIdsWithDialogueBeforeDate(
                userId, List.copyOf(firstDialogueStartByPartnerUserId.keySet()), date);

        firstDialogueStartByPartnerUserId.keySet().removeAll(alreadyMetPartnerUserIds);
        return firstDialogueStartByPartnerUserId;
    }

    private void scheduleFirstMeetings(Long userId, LocalDate date, Map<Long, LocalDateTime> firstMeetingStartByPartnerUserId) {
        firstMeetingStartByPartnerUserId.forEach((partnerUserId, start) ->
                appNotificationScheduleRepository.insertIfAbsent(
                        userId, partnerUserId, AppNotificationScheduleType.FIRST_MEETING.name(), date, KstTimes.toInstant(start)));
    }

    private Map<Long, LocalDateTime> firstDialogueStartByPartnerUserId(Long userId, List<SimulationsSceneCommand> commands) {
        return commands.stream()
                .filter(SimulationsDialogueSceneCommand.class::isInstance)
                .map(SimulationsDialogueSceneCommand.class::cast)
                .flatMap(dialogue -> distinct(dialogue.with()).stream()
                        .filter(partnerUserId -> !partnerUserId.equals(userId))
                        .map(partnerUserId -> Map.entry(partnerUserId, dialogue.start())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, this::earlier, LinkedHashMap::new));
    }

    private LocalDateTime earlier(LocalDateTime first, LocalDateTime second) {
        return first.isBefore(second) ? first : second;
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

    private void saveRelationships(Long userId, LocalDate date, String version, List<SimulationsRelationshipCommand> commands,
                                   Map<Long, LocalDateTime> firstMeetingStartByPartnerUserId, Instant intimacyAsOf) {
        Map<Long, LocalDateTime> becameFriendTimeByPartnerUserId = becameFriendTimeByPartnerUserId(userId, date, commands);

        List<Relationship> aiRelationships = commands.stream()
                .map(command -> Relationship.create(userId, date, version, command.partnerId(), command.rapport(),
                        command.partnerModel(), command.updateTime(), intimacyAsOf))
                .toList();
        List<Relationship> relationships = Stream.concat(
                        aiRelationships.stream(),
                        firstMeetingRelationships(userId, date, version, commands, firstMeetingStartByPartnerUserId, intimacyAsOf).stream())
                .toList();
        relationshipRepository.saveAll(relationships);

        relationships.stream()
                .map(Relationship::getPartnerUserId)
                .distinct()
                .forEach(partnerUserId -> encounterRepository.upsert(
                        Math.min(userId, partnerUserId), Math.max(userId, partnerUserId)));

        becameFriendTimeByPartnerUserId.forEach((partnerUserId, updateTime) ->
                appNotificationScheduleRepository.insertIfAbsent(
                        userId, partnerUserId, AppNotificationScheduleType.FRIEND.name(), date, KstTimes.toInstant(updateTime)));

        aiRelationships.forEach(relationship -> openChatRoom(userId, relationship.getPartnerUserId(),
                intimacyReader.readBonuses(userId, relationship.getPartnerUserId()).intimacyOf(relationship, Instant.now()).value(),
                relationship.getUpdateTime()));
    }

    private List<Relationship> firstMeetingRelationships(Long userId, LocalDate date, String version,
                                                         List<SimulationsRelationshipCommand> commands,
                                                         Map<Long, LocalDateTime> firstMeetingStartByPartnerUserId,
                                                         Instant intimacyAsOf) {
        Map<Long, LocalDateTime> earliestUpdateTimeByPartnerUserId = commands.stream()
                .collect(Collectors.toMap(SimulationsRelationshipCommand::partnerId,
                        SimulationsRelationshipCommand::updateTime, this::earlier));

        return firstMeetingStartByPartnerUserId.entrySet().stream()
                .filter(entry -> !earliestUpdateTimeByPartnerUserId.containsKey(entry.getKey())
                        || earliestUpdateTimeByPartnerUserId.get(entry.getKey()).isAfter(entry.getValue()))
                .filter(entry -> relationshipRepository.findLatestByUserIdAndPartnerUserIdBeforeDate(userId, entry.getKey(), date).isEmpty())
                .map(entry -> Relationship.create(userId, date, version, entry.getKey(), FIRST_MEETING_INTIMACY,
                        FIRST_MEETING_PARTNER_MODEL, entry.getValue(), intimacyAsOf))
                .toList();
    }

    private Map<Long, LocalDateTime> becameFriendTimeByPartnerUserId(Long userId, LocalDate date, List<SimulationsRelationshipCommand> commands) {
        return commands.stream()
                .filter(command -> becameFriend(userId, date, command))
                .collect(Collectors.toMap(SimulationsRelationshipCommand::partnerId,
                        SimulationsRelationshipCommand::updateTime, this::earlier, LinkedHashMap::new));
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
            InfoLog.log(log, "상대 쪽에서 채팅방을 먼저 열어 개설을 건너뜁니다.", field("userId", userId), field("partnerUserId", partnerUserId));
        }
    }

    private List<Long> distinct(List<Long> userIds) {
        if (userIds == null) {
            return List.of();
        }

        return userIds.stream().distinct().toList();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SimulationPersonaResult persona(Long userId, LocalDate date) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.isWithdrawn()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (!entitlementReader.hasSimulationAccess(userId)) {
            purchaseService.syncQuietly(user);

            if (!entitlementReader.hasSimulationAccess(userId)) {
                throw new BusinessException(ErrorCode.SIMULATION_ACCESS_REQUIRED);
            }
        }
        if (!consentReader.hasAgreedAllRequired(userId, PolicyKind.PARALLEL_ENTRY)) {
            throw new BusinessException(ErrorCode.SIMULATION_CONSENT_REQUIRED);
        }

        Map<PersonaDimension, List<String>> personaElements = personaElementRepository.findAllByUserIdOrderByIdAsc(userId).stream()
                .collect(Collectors.groupingBy(
                        PersonaElement::getDimension,
                        LinkedHashMap::new,
                        Collectors.mapping(PersonaElement::getExplanation, Collectors.toList())
                ));

        LocalDateTime intimacyAsOf = KstTimes.now().minus(INTIMACY_AS_OF_MARGIN).truncatedTo(ChronoUnit.SECONDS);
        List<SimulationPersonaIntimacyResult> intimacies = intimacyReader
                .readForSimulation(userId, date, KstTimes.toInstant(intimacyAsOf)).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new SimulationPersonaIntimacyResult(entry.getKey(), entry.getValue()))
                .toList();

        return new SimulationPersonaResult(
                user.getId(),
                user.getFamilyName(),
                user.getGivenName(),
                user.getNickname(),
                user.getGender(),
                user.getOrganization(),
                user.getAffiliation(),
                birthDate(user),
                personaElements,
                user.getPoolNumber(),
                intimacyAsOf,
                intimacies
        );
    }

    private LocalDate birthDate(User user) {
        return user.getBirthDate() == null ? null : LocalDate.parse(user.getBirthDate());
    }
}
