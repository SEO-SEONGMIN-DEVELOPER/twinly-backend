package com.nidus.twinly.simulation.service;

import com.nidus.twinly.activity.domain.QuestionType;
import com.nidus.twinly.activity.domain.SceneType;
import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.entity.ScenePartner;
import com.nidus.twinly.activity.repository.QuestionPartnerRepository;
import com.nidus.twinly.activity.repository.QuestionRepository;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.chat.opener.ChatRoomOpener;
import com.nidus.twinly.chat.repository.ChatRoomOpeningRepository;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.notification.domain.AppNotificationScheduleType;
import com.nidus.twinly.notification.repository.AppNotificationScheduleRepository;
import com.nidus.twinly.people.repository.EncounterRepository;
import com.nidus.twinly.relationship.domain.IntimacyBonuses;
import com.nidus.twinly.relationship.entity.IntimacyBonus;
import com.nidus.twinly.relationship.entity.Relationship;
import com.nidus.twinly.relationship.reader.IntimacyReader;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.simulation.dto.command.SimulationsActionSceneCommand;
import com.nidus.twinly.simulation.dto.command.SimulationsCommand;
import com.nidus.twinly.simulation.dto.command.SimulationsDialogueSceneCommand;
import com.nidus.twinly.simulation.dto.command.SimulationsMoveSceneCommand;
import com.nidus.twinly.simulation.dto.command.SimulationsQuestionCommand;
import com.nidus.twinly.simulation.dto.command.SimulationsRelationshipCommand;
import com.nidus.twinly.simulation.dto.command.SimulationsSceneCommand;
import com.nidus.twinly.simulation.dto.result.SimulationPersonaIntimacyResult;
import com.nidus.twinly.simulation.dto.result.SimulationPersonaResult;
import com.nidus.twinly.user.entity.PersonaElement;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.legal.domain.PolicyKind;
import com.nidus.twinly.legal.reader.ConsentReader;
import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.purchase.service.PurchaseService;
import com.nidus.twinly.user.repository.PersonaElementRepository;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SimulationServiceUnitTest {

    private static final Long USER_ID = 12L;
    private static final Long PARTNER_ID = 34L;
    private static final LocalDate DATE = LocalDate.of(2026, 8, 3);
    private static final LocalDate FUTURE_DATE = KstTimes.today().plusDays(1);

    @Mock
    SceneRepository sceneRepository;

    @Mock
    ScenePartnerRepository scenePartnerRepository;

    @Mock
    QuestionRepository questionRepository;

    @Mock
    QuestionPartnerRepository questionPartnerRepository;

    @Mock
    RelationshipRepository relationshipRepository;

    @Mock
    EncounterRepository encounterRepository;

    @Mock
    ChatRoomOpener chatRoomOpener;

    @Mock
    ChatRoomOpeningRepository chatRoomOpeningRepository;

    @Mock
    AppNotificationScheduleRepository appNotificationScheduleRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    PersonaElementRepository personaElementRepository;

    @Mock
    EntitlementReader entitlementReader;

    @Mock
    ConsentReader consentReader;

    @Mock
    PurchaseService purchaseService;

    @Mock
    IntimacyReader intimacyReader;

    SimulationService simulationService;

    @BeforeEach
    void setUp() {
        simulationService = new SimulationService(
                sceneRepository, scenePartnerRepository, questionRepository, questionPartnerRepository,
                relationshipRepository, encounterRepository, chatRoomOpener, chatRoomOpeningRepository,
                appNotificationScheduleRepository, userRepository,
                personaElementRepository, entitlementReader, consentReader, purchaseService, new ObjectMapper(), intimacyReader);
        lenient().when(intimacyReader.readBonuses(any(), any())).thenReturn(new IntimacyBonuses(List.of()));
    }

    @Test
    @DisplayName("친밀도가 친구 기준을 처음 넘으면 관계가 갱신된 시각으로 친구 알림을 예약한다")
    void simulations_schedules_friend_when_relationship_type_becomes_friend() {
        // given: 직전 관계는 지인(20)이고 이번 시뮬레이션에서 친구 기준(35)을 넘김
        givenEmptyPreviousSimulation();
        given(relationshipRepository.findLatestByUserIdAndPartnerUserIdBeforeDate(USER_ID, PARTNER_ID, DATE))
                .willReturn(Optional.of(relationship(20)));

        // when: 시뮬레이션 결과 저장
        simulationService.simulations(USER_ID, simulationsCommand(35));

        // then: 시각이 이미 지났어도 직접 보내지 않고 예약으로 남겨, 같은 날짜가 다시 저장돼도 두 번 나가지 않게 한다
        then(appNotificationScheduleRepository).should().insertIfAbsent(
                USER_ID, PARTNER_ID, AppNotificationScheduleType.FRIEND.name(), DATE, KstTimes.toInstant(DATE.atTime(21, 0)));
    }

    @Test
    @DisplayName("직전 관계가 없어도 첫 시뮬레이션에서 친구 기준을 넘으면 친구 알림을 예약한다")
    void simulations_schedules_friend_without_previous_relationship() {
        // given: 직전 관계 기록이 없음
        givenEmptyPreviousSimulation();
        given(relationshipRepository.findLatestByUserIdAndPartnerUserIdBeforeDate(USER_ID, PARTNER_ID, DATE))
                .willReturn(Optional.empty());

        // when: 친구 기준을 넘는 친밀도로 저장
        simulationService.simulations(USER_ID, simulationsCommand(35));

        // then: 지인에서 올라온 것으로 보고 예약함
        then(appNotificationScheduleRepository).should().insertIfAbsent(
                USER_ID, PARTNER_ID, AppNotificationScheduleType.FRIEND.name(), DATE, KstTimes.toInstant(DATE.atTime(21, 0)));
    }

    @Test
    @DisplayName("이미 친구 이상이던 상대는 친밀도가 더 올라도 친구 알림을 예약하지 않는다")
    void simulations_does_not_schedule_friend_when_already_friend() {
        // given: 직전 관계가 이미 친구(40)
        givenEmptyPreviousSimulation();
        given(relationshipRepository.findLatestByUserIdAndPartnerUserIdBeforeDate(USER_ID, PARTNER_ID, DATE))
                .willReturn(Optional.of(relationship(40)));

        // when: 친밀도가 더 오른 채로 저장
        simulationService.simulations(USER_ID, simulationsCommand(60));

        // then: 경계를 새로 넘은 것이 아니므로 예약 없음
        then(appNotificationScheduleRepository).should(never()).insertIfAbsent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("친구 기준에 못 미치면 친구 알림을 예약하지 않는다")
    void simulations_does_not_schedule_friend_below_threshold() {
        // given: 직전 관계도 지인이고 이번에도 지인
        givenEmptyPreviousSimulation();
        given(relationshipRepository.findLatestByUserIdAndPartnerUserIdBeforeDate(USER_ID, PARTNER_ID, DATE))
                .willReturn(Optional.of(relationship(10)));

        // when: 친구 기준 미만으로 저장
        simulationService.simulations(USER_ID, simulationsCommand(34));

        // then: 예약 없음
        then(appNotificationScheduleRepository).should(never()).insertIfAbsent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("친구가 되는 시각이 아직 오지 않았으면 그 미래 시각으로 예약한다")
    void simulations_schedules_friend_when_update_time_is_future() {
        // given: 내일 21시에 친구 기준을 넘는 결과가 미리 들어옴
        givenEmptyPreviousSimulation(FUTURE_DATE);
        given(relationshipRepository.findLatestByUserIdAndPartnerUserIdBeforeDate(USER_ID, PARTNER_ID, FUTURE_DATE))
                .willReturn(Optional.of(relationship(20)));

        // when: 시뮬레이션 결과 저장
        simulationService.simulations(USER_ID, simulationsCommand(FUTURE_DATE, 35));

        // then: 저장 시점에 알리면 아직 일어나지 않은 일을 미리 알리게 되므로, 관계가 갱신되는 시각으로 예약만 한다
        then(appNotificationScheduleRepository).should().insertIfAbsent(
                USER_ID, PARTNER_ID, AppNotificationScheduleType.FRIEND.name(), FUTURE_DATE,
                KstTimes.toInstant(FUTURE_DATE.atTime(21, 0)));
    }

    @Test
    @DisplayName("같은 날짜 결과를 다시 저장하면 아직 안 나간 예약을 먼저 지운다")
    void simulations_deletes_unsent_schedules_before_saving() {
        // given: 이전 결과가 없는 날짜
        givenEmptyPreviousSimulation();

        // when: 시뮬레이션 결과 저장
        simulationService.simulations(USER_ID, new SimulationsCommand(USER_ID, DATE, List.of(), List.of(), List.of(), null));

        // then: 바뀐 결과에 없는 일이 예약된 채 남아 발송되지 않도록 미발송 예약을 비운다
        then(appNotificationScheduleRepository).should().deleteAllUnsentByUserIdAndSimulationDate(USER_ID, DATE);
    }

    @Test
    @DisplayName("처음 대화하는 상대가 있으면 그날 가장 이른 대화 장면 시작 시각으로 첫 만남 알림을 예약한다")
    void simulations_schedules_first_meeting_at_earliest_dialogue_start() {
        // given: 내일 같은 상대와 대화 장면이 두 번 있고, 이전 날짜에 대화한 적은 없음
        givenEmptyPreviousSimulation(FUTURE_DATE);
        given(scenePartnerRepository.findPartnerUserIdsWithDialogueBeforeDate(USER_ID, List.of(PARTNER_ID), FUTURE_DATE))
                .willReturn(List.of());
        SimulationsCommand command = new SimulationsCommand(USER_ID, FUTURE_DATE, List.of(
                dialogueScene(FUTURE_DATE, 15, List.of(PARTNER_ID)),
                dialogueScene(FUTURE_DATE, 11, List.of(PARTNER_ID))
        ), List.of(), List.of(), null);

        // when: 시뮬레이션 결과 저장
        simulationService.simulations(USER_ID, command);

        // then: 입력 순서와 무관하게 더 이른 11시 장면으로 한 번만 예약한다
        then(appNotificationScheduleRepository).should().insertIfAbsent(
                USER_ID, PARTNER_ID, AppNotificationScheduleType.FIRST_MEETING.name(), FUTURE_DATE,
                KstTimes.toInstant(FUTURE_DATE.atTime(11, 0)));
        then(appNotificationScheduleRepository).should(never()).insertIfAbsent(
                USER_ID, PARTNER_ID, AppNotificationScheduleType.FIRST_MEETING.name(), FUTURE_DATE,
                KstTimes.toInstant(FUTURE_DATE.atTime(15, 0)));
    }

    @Test
    @DisplayName("이전 날짜에 이미 대화한 상대는 첫 만남 알림을 예약하지 않는다")
    void simulations_does_not_schedule_first_meeting_for_already_met_partner() {
        // given: 내일 대화 장면이 있지만 그 상대와는 이전 날짜에 대화한 기록이 있음
        givenEmptyPreviousSimulation(FUTURE_DATE);
        given(scenePartnerRepository.findPartnerUserIdsWithDialogueBeforeDate(USER_ID, List.of(PARTNER_ID), FUTURE_DATE))
                .willReturn(List.of(PARTNER_ID));
        SimulationsCommand command = new SimulationsCommand(USER_ID, FUTURE_DATE, List.of(
                dialogueScene(FUTURE_DATE, 11, List.of(PARTNER_ID))
        ), List.of(), List.of(), null);

        // when: 시뮬레이션 결과 저장
        simulationService.simulations(USER_ID, command);

        // then: 처음이 아니므로 예약 없음
        then(appNotificationScheduleRepository).should(never()).insertIfAbsent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("대화 장면 시작 시각이 이미 지났어도 그 시각으로 예약해 다음 발송 주기에 바로 나가게 한다")
    void simulations_schedules_first_meeting_for_past_dialogue() {
        // given: 이미 지난 날짜의 대화 장면, 이전 날짜에 대화한 적은 없음
        givenEmptyPreviousSimulation();
        given(scenePartnerRepository.findPartnerUserIdsWithDialogueBeforeDate(USER_ID, List.of(PARTNER_ID), DATE))
                .willReturn(List.of());
        SimulationsCommand command = new SimulationsCommand(USER_ID, DATE, List.of(dialogueScene("카페")), List.of(), List.of(), null);

        // when: 시뮬레이션 결과 저장
        simulationService.simulations(USER_ID, command);

        // then: 직접 보내지 않고 예약으로 남겨야 같은 날짜가 다시 저장돼도 두 번 나가지 않는다
        then(appNotificationScheduleRepository).should().insertIfAbsent(
                USER_ID, PARTNER_ID, AppNotificationScheduleType.FIRST_MEETING.name(), DATE,
                KstTimes.toInstant(DATE.atTime(11, 0)));
    }

    @Test
    @DisplayName("대화가 아닌 행동 장면에 함께 있던 상대와 자기 자신은 첫 만남 알림 대상이 아니다")
    void simulations_does_not_schedule_first_meeting_for_action_scene_or_self() {
        // given: 내일 상대와 함께한 행동 장면, 그리고 참여자에 본인만 있는 대화 장면
        givenEmptyPreviousSimulation(FUTURE_DATE);
        SimulationsCommand command = new SimulationsCommand(USER_ID, FUTURE_DATE, List.of(
                new SimulationsActionSceneCommand(FUTURE_DATE.atTime(9, 0), FUTURE_DATE.atTime(10, 0), "action", "학교",
                        null, List.of(PARTNER_ID), "narration", null),
                dialogueScene(FUTURE_DATE, 11, List.of(USER_ID))
        ), List.of(), List.of(), null);

        // when: 시뮬레이션 결과 저장
        simulationService.simulations(USER_ID, command);

        // then: 대화를 나눈 상대가 없으므로 예약 없음
        then(appNotificationScheduleRepository).should(never()).insertIfAbsent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("같은 상대의 관계가 여러 건 들어오면 친구 기준을 넘은 가장 이른 시각으로 한 번만 예약한다")
    void simulations_schedules_friend_once_at_earliest_update_time() {
        // given: 같은 상대에 대해 친구 기준을 넘는 관계가 21시, 15시 두 건
        givenEmptyPreviousSimulation();
        given(relationshipRepository.findLatestByUserIdAndPartnerUserIdBeforeDate(USER_ID, PARTNER_ID, DATE))
                .willReturn(Optional.of(relationship(20)));
        SimulationsCommand command = new SimulationsCommand(USER_ID, DATE, List.of(), List.of(), List.of(
                new SimulationsRelationshipCommand(PARTNER_ID, DATE.atTime(21, 0), 40, "{}"),
                new SimulationsRelationshipCommand(PARTNER_ID, DATE.atTime(15, 0), 36, "{}")
        ), null);

        // when: 시뮬레이션 결과 저장
        simulationService.simulations(USER_ID, command);

        // then: 입력 순서와 무관하게 더 이른 15시로 한 번만 예약한다
        then(appNotificationScheduleRepository).should().insertIfAbsent(
                USER_ID, PARTNER_ID, AppNotificationScheduleType.FRIEND.name(), DATE, KstTimes.toInstant(DATE.atTime(15, 0)));
        then(appNotificationScheduleRepository).should(never()).insertIfAbsent(
                USER_ID, PARTNER_ID, AppNotificationScheduleType.FRIEND.name(), DATE, KstTimes.toInstant(DATE.atTime(21, 0)));
    }

    @Test
    @DisplayName("한 대화 장면에 상대가 여럿이면 처음 대화하는 상대만 첫 만남 알림을 예약한다")
    void simulations_schedules_first_meeting_only_for_new_partner_in_group_dialogue() {
        // given: 두 사람과 함께한 대화 장면, 그중 한 사람과는 이전 날짜에 대화한 기록이 있음
        Long newPartnerId = 56L;
        givenEmptyPreviousSimulation();
        given(scenePartnerRepository.findPartnerUserIdsWithDialogueBeforeDate(USER_ID, List.of(PARTNER_ID, newPartnerId), DATE))
                .willReturn(List.of(PARTNER_ID));
        SimulationsCommand command = new SimulationsCommand(USER_ID, DATE, List.of(
                dialogueScene(DATE, 11, List.of(PARTNER_ID, newPartnerId))
        ), List.of(), List.of(), null);

        // when: 시뮬레이션 결과 저장
        simulationService.simulations(USER_ID, command);

        // then: 같은 장면에 있었어도 처음 만난 상대에 대해서만 예약한다
        then(appNotificationScheduleRepository).should().insertIfAbsent(
                USER_ID, newPartnerId, AppNotificationScheduleType.FIRST_MEETING.name(), DATE, KstTimes.toInstant(DATE.atTime(11, 0)));
        then(appNotificationScheduleRepository).should(never()).insertIfAbsent(
                eq(USER_ID), eq(PARTNER_ID), any(), any(), any());
    }

    @Test
    @DisplayName("처음 대화하는 상대는 첫 대화 시작 시각에 친밀도 0 관계를 함께 저장하고, AI가 관계를 보내지 않은 상대도 저장한다")
    void simulations_saves_zero_intimacy_relationship_at_first_dialogue_start() {
        // given: 두 사람과 11시에 처음 대화했고, AI는 그중 한 사람의 관계만 21시로 보냈다
        Long newPartnerId = 56L;
        givenEmptyPreviousSimulation();
        given(scenePartnerRepository.findPartnerUserIdsWithDialogueBeforeDate(USER_ID, List.of(PARTNER_ID, newPartnerId), DATE))
                .willReturn(List.of());
        SimulationsCommand command = new SimulationsCommand(USER_ID, DATE, List.of(
                dialogueScene(DATE, 11, List.of(PARTNER_ID, newPartnerId))
        ), List.of(), List.of(
                new SimulationsRelationshipCommand(PARTNER_ID, DATE.atTime(21, 0), 20, "AI가 파악한 상대")
        ), null);

        // when: 시뮬레이션 결과 저장
        simulationService.simulations(USER_ID, command);

        // then: AI 관계는 그대로, 두 상대 모두 11시에 친밀도 0 관계가 더해져 첫 대화부터 사람 목록에 보인다
        ArgumentCaptor<List<Relationship>> captor = ArgumentCaptor.captor();
        then(relationshipRepository).should().saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(Relationship::getPartnerUserId, Relationship::getIntimacy, Relationship::getUpdateTime, Relationship::getPartnerModel)
                .containsExactlyInAnyOrder(
                        tuple(PARTNER_ID, 20, DATE.atTime(21, 0), "AI가 파악한 상대"),
                        tuple(PARTNER_ID, 0, DATE.atTime(11, 0), "아직 알게된 점이 없습니다."),
                        tuple(newPartnerId, 0, DATE.atTime(11, 0), "아직 알게된 점이 없습니다."));
    }

    @Test
    @DisplayName("AI가 돌려준 친밀도 기준 시각을 AI 관계와 첫 만남 관계 모두에 저장한다")
    void simulations_saves_intimacy_as_of_on_all_relationships() {
        // given: 11시에 처음 대화했고, AI는 21시 관계와 기준 시각을 함께 보냈다
        Instant intimacyAsOf = Instant.parse("2026-07-27T00:30:00.123456Z");
        givenEmptyPreviousSimulation();
        given(scenePartnerRepository.findPartnerUserIdsWithDialogueBeforeDate(USER_ID, List.of(PARTNER_ID), DATE))
                .willReturn(List.of());
        SimulationsCommand command = new SimulationsCommand(USER_ID, DATE, List.of(
                dialogueScene(DATE, 11, List.of(PARTNER_ID))
        ), List.of(), List.of(
                new SimulationsRelationshipCommand(PARTNER_ID, DATE.atTime(21, 0), 20, "AI가 파악한 상대")
        ), intimacyAsOf);

        // when: 시뮬레이션 결과 저장
        simulationService.simulations(USER_ID, command);

        // then: AI 관계와 첫 만남 0점 관계 모두 같은 기준 시각을 가진다
        ArgumentCaptor<List<Relationship>> captor = ArgumentCaptor.captor();
        then(relationshipRepository).should().saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(Relationship::getIntimacy, Relationship::getIntimacyAsOf)
                .containsExactlyInAnyOrder(
                        tuple(20, intimacyAsOf),
                        tuple(0, intimacyAsOf));
    }

    @Test
    @DisplayName("AI 관계 갱신 시각이 첫 대화 시작과 같거나 더 이르면 친밀도 0 관계를 넣지 않는다")
    void simulations_skips_zero_intimacy_relationship_when_ai_relationship_is_not_later() {
        // given: 11시에 두 사람과 처음 대화했고, AI 관계는 한 사람은 11시 정각, 다른 사람은 10시
        Long newPartnerId = 56L;
        givenEmptyPreviousSimulation();
        given(scenePartnerRepository.findPartnerUserIdsWithDialogueBeforeDate(USER_ID, List.of(PARTNER_ID, newPartnerId), DATE))
                .willReturn(List.of());
        SimulationsCommand command = new SimulationsCommand(USER_ID, DATE, List.of(
                dialogueScene(DATE, 11, List.of(PARTNER_ID, newPartnerId))
        ), List.of(), List.of(
                new SimulationsRelationshipCommand(PARTNER_ID, DATE.atTime(11, 0), 20, "{}"),
                new SimulationsRelationshipCommand(newPartnerId, DATE.atTime(10, 0), 20, "{}")
        ), null);

        // when: 시뮬레이션 결과 저장
        simulationService.simulations(USER_ID, command);

        // then: 0점 관계가 더 늦은 기록이 되어 AI 친밀도를 가리거나, 같은 시각으로 유니크 키에 걸리는 일이 없다
        ArgumentCaptor<List<Relationship>> captor = ArgumentCaptor.captor();
        then(relationshipRepository).should().saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(Relationship::getIntimacy).containsOnly(20);
    }

    @Test
    @DisplayName("이전 날짜에 관계 기록이 있는 상대는 처음 대화해도 친밀도 0 관계를 넣지 않는다")
    void simulations_skips_zero_intimacy_relationship_when_previous_relationship_exists() {
        // given: 오늘 처음 대화했지만 대화 없이 쌓인 이전 날짜 관계 기록이 있다
        givenEmptyPreviousSimulation();
        given(scenePartnerRepository.findPartnerUserIdsWithDialogueBeforeDate(USER_ID, List.of(PARTNER_ID), DATE))
                .willReturn(List.of());
        given(relationshipRepository.findLatestByUserIdAndPartnerUserIdBeforeDate(USER_ID, PARTNER_ID, DATE))
                .willReturn(Optional.of(relationship(20)));
        SimulationsCommand command = new SimulationsCommand(USER_ID, DATE, List.of(
                dialogueScene(DATE, 11, List.of(PARTNER_ID))
        ), List.of(), List.of(), null);

        // when: 시뮬레이션 결과 저장
        simulationService.simulations(USER_ID, command);

        // then: 이미 목록에 보이는 상대의 친밀도를 0으로 떨어뜨리지 않는다
        ArgumentCaptor<List<Relationship>> captor = ArgumentCaptor.captor();
        then(relationshipRepository).should().saveAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    @DisplayName("미발송 예약 삭제는 새 예약 저장보다 먼저 일어난다")
    void simulations_deletes_unsent_schedules_before_inserting_new_ones() {
        // given: 친구 기준을 넘는 결과
        givenEmptyPreviousSimulation();
        given(relationshipRepository.findLatestByUserIdAndPartnerUserIdBeforeDate(USER_ID, PARTNER_ID, DATE))
                .willReturn(Optional.of(relationship(20)));

        // when: 시뮬레이션 결과 저장
        simulationService.simulations(USER_ID, simulationsCommand(35));

        // then: 순서가 뒤집히면 방금 넣은 예약이 지워지거나, 남아 있던 이전 예약에 막혀 새 시각이 반영되지 않는다
        InOrder inOrder = inOrder(appNotificationScheduleRepository);
        inOrder.verify(appNotificationScheduleRepository).deleteAllUnsentByUserIdAndSimulationDate(USER_ID, DATE);
        inOrder.verify(appNotificationScheduleRepository).insertIfAbsent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("경로의 userId와 본문의 userId가 다르면 INVALID_REQUEST 예외가 발생하고 예약을 건드리지 않는다")
    void simulations_with_mismatched_user_id_throws() {
        // given: 본문의 userId가 경로의 userId와 다른 요청
        SimulationsCommand command = new SimulationsCommand(PARTNER_ID, DATE, List.of(), List.of(), List.of(), null);

        // when & then: INVALID_REQUEST 예외가 나고, 다른 유저의 예약을 지우거나 새로 만들지 않는다
        assertThatThrownBy(() -> simulationService.simulations(USER_ID, command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        then(appNotificationScheduleRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("존재하지 않는 유저의 결과를 저장하려 하면 USER_NOT_FOUND 예외가 발생하고 예약을 건드리지 않는다")
    void simulations_for_unknown_user_throws() {
        // given: 유저 행이 없음
        given(userRepository.existsById(USER_ID)).willReturn(false);

        // when & then: USER_NOT_FOUND 예외가 나고 저장도 예약도 일어나지 않는다
        assertThatThrownBy(() -> simulationService.simulations(USER_ID, simulationsCommand(35)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        then(appNotificationScheduleRepository).shouldHaveNoInteractions();
        then(sceneRepository).should(never()).saveAll(any());
    }

    @Test
    @DisplayName("장면의 상대 중 존재하지 않는 유저가 있으면 INVALID_REQUEST 예외가 발생하고 이전 결과를 지우지 않는다")
    void simulations_with_unknown_scene_partner_throws_before_deleting() {
        // given: 대화 상대 둘 중 한 명만 실제 유저
        given(userRepository.existsById(USER_ID)).willReturn(true);
        given(userRepository.countByIdIn(Set.of(PARTNER_ID, 999L))).willReturn(1L);
        SimulationsCommand command = new SimulationsCommand(USER_ID, DATE, List.of(
                dialogueScene(DATE, 11, List.of(PARTNER_ID, 999L))
        ), List.of(), List.of(), null);

        // when & then: 외래 키 위반으로 서버 오류가 나기 전에 요청 오류로 끊고, 기존 결과와 예약은 그대로 둔다
        assertThatThrownBy(() -> simulationService.simulations(USER_ID, command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        then(sceneRepository).should(never()).findAllByUserIdAndDate(any(), any());
        then(sceneRepository).should(never()).saveAll(any());
        then(appNotificationScheduleRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("질문·관계의 상대까지 한 번에 모아 존재 여부를 확인한다")
    void simulations_validates_partners_of_questions_and_relationships_together() {
        // given: 장면에는 상대가 없고, 질문과 관계에만 서로 다른 상대가 있으며 그중 관계의 상대가 없는 유저
        given(userRepository.existsById(USER_ID)).willReturn(true);
        given(userRepository.countByIdIn(Set.of(PARTNER_ID, 999L))).willReturn(1L);
        SimulationsCommand command = new SimulationsCommand(USER_ID, DATE, List.of(),
                List.of(new SimulationsQuestionCommand(DATE.atTime(21, 0), QuestionType.PROMISE, List.of(PARTNER_ID), "오늘 어땠어?", List.of())),
                List.of(new SimulationsRelationshipCommand(999L, DATE.atTime(21, 0), 40, "{}")), null);

        // when & then: 어느 항목에서 나온 상대든 하나라도 없으면 요청 오류다
        assertThatThrownBy(() -> simulationService.simulations(USER_ID, command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        then(relationshipRepository).should(never()).saveAll(any());
    }

    @Test
    @DisplayName("장면 장소의 ':' 구분자는 공백으로 바꿔 저장한다")
    void simulations_replaces_place_separator_with_space() {
        // given: 장소가 "A:B:C" 형태로 들어옴
        givenEmptyPreviousSimulation();
        SimulationsCommand command = new SimulationsCommand(USER_ID, DATE, List.of(
                actionScene("학교:정문:앞"),
                dialogueScene("카페:2층")
        ), List.of(), List.of(), null);

        // when: 시뮬레이션 결과 저장
        simulationService.simulations(USER_ID, command);

        // then: 저장되는 Scene의 place에서 ':'가 공백으로 치환됨
        ArgumentCaptor<List<Scene>> captor = ArgumentCaptor.captor();
        then(sceneRepository).should().saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(Scene::getPlace)
                .containsExactly("학교 정문 앞", "카페 2층");
    }

    @Test
    @DisplayName("장면 장소 코드는 받은 그대로 저장하고, 보내지 않은 장면은 null로 저장한다")
    void simulations_saves_place_code_as_is() {
        // given: 장소 코드가 있는 행동 장면과 장소 코드가 없는 대화 장면
        givenEmptyPreviousSimulation();
        SimulationsCommand command = new SimulationsCommand(USER_ID, DATE, List.of(
                new SimulationsActionSceneCommand(DATE.atTime(9, 0), DATE.atTime(10, 0), "action", "학교 정문",
                        "SCHOOL_GATE", List.of(), "narration", null),
                dialogueScene("카페")
        ), List.of(), List.of(), null);

        // when: 시뮬레이션 결과 저장
        simulationService.simulations(USER_ID, command);

        // then: 저장되는 Scene의 placeCode는 가공 없이 그대로, 없으면 null
        ArgumentCaptor<List<Scene>> captor = ArgumentCaptor.captor();
        then(sceneRepository).should().saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(Scene::getPlaceCode)
                .containsExactly("SCHOOL_GATE", null);
    }

    @Test
    @DisplayName("이동 장면은 출발지·도착지·이동 수단·지도 버전을 담은 MOVE 장면으로 저장하고, 두 장소의 ':' 구분자를 공백으로 바꾼다")
    void simulations_saves_move_scene() {
        // given: 집에서 카페로 걸어간 이동 장면, 두 장소에 ':' 구분자가 들어 있음
        givenEmptyPreviousSimulation();
        SimulationsCommand command = new SimulationsCommand(USER_ID, DATE, List.of(
                moveScene("성북구:주택가:집", "성북구:학생 거리:탐앤탐스", List.of())
        ), List.of(), List.of(), null);

        // when: 시뮬레이션 결과 저장
        simulationService.simulations(USER_ID, command);

        // then: MOVE 장면 하나가 출발지·도착지·이동 정보를 담고, 장소 코드와 지도 버전은 가공 없이 저장됨
        ArgumentCaptor<List<Scene>> captor = ArgumentCaptor.captor();
        then(sceneRepository).should().saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(scene -> {
            assertThat(scene.getType()).isEqualTo(SceneType.MOVE);
            assertThat(scene.getStartsAt()).isEqualTo(DATE.atTime(11, 0));
            assertThat(scene.getEndsAt()).isEqualTo(DATE.atTime(11, 10));
            assertThat(scene.getFromPlace()).isEqualTo("성북구 주택가 집");
            assertThat(scene.getFromPlaceCode()).isEqualTo("P0258");
            assertThat(scene.getPlace()).isEqualTo("성북구 학생 거리 탐앤탐스");
            assertThat(scene.getPlaceCode()).isEqualTo("P0158");
            assertThat(scene.getTravelMode()).isEqualTo("walk");
            assertThat(scene.getMapVersion()).isEqualTo("sha256:89af");
            assertThat(scene.getNarration()).isEqualTo("카페로 향했다");
            assertThat(scene.getMind()).isNull();
            assertThat(scene.getLines()).isNull();
        });
    }

    @Test
    @DisplayName("함께 이동한 상대는 이동 장면의 상대로 저장하지만 대화가 아니므로 첫 만남 알림 대상은 아니다")
    void simulations_saves_move_partner_without_first_meeting() {
        // given: 상대와 함께 카페로 이동한 장면, 저장된 장면에는 id 가 붙는다
        given(userRepository.existsById(USER_ID)).willReturn(true);
        given(userRepository.countByIdIn(Set.of(PARTNER_ID))).willReturn(1L);
        given(sceneRepository.findAllByUserIdAndDate(USER_ID, DATE)).willReturn(List.of());
        given(questionRepository.findAllByUserIdAndDate(USER_ID, DATE)).willReturn(List.of());
        given(sceneRepository.saveAll(any())).willAnswer(invocation -> {
            List<Scene> scenes = invocation.getArgument(0);
            scenes.forEach(scene -> ReflectionTestUtils.setField(scene, "id", 70L));
            return scenes;
        });
        given(questionRepository.saveAll(any())).willReturn(List.of());
        SimulationsCommand command = new SimulationsCommand(USER_ID, DATE, List.of(
                moveScene("집", "학생 거리 탐앤탐스", List.of(PARTNER_ID))
        ), List.of(), List.of(), null);

        // when: 시뮬레이션 결과 저장
        simulationService.simulations(USER_ID, command);

        // then: 상대가 이동 장면에 붙어 저장됨 + 첫 만남 알림은 예약하지 않음
        ArgumentCaptor<List<ScenePartner>> captor = ArgumentCaptor.captor();
        then(scenePartnerRepository).should().saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(ScenePartner::getSceneId, ScenePartner::getUserId)
                .containsExactly(tuple(70L, PARTNER_ID));
        then(appNotificationScheduleRepository).should(never()).insertIfAbsent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("함께 이동한 상대가 존재하지 않는 유저면 INVALID_REQUEST 예외가 발생하고 이전 결과를 지우지 않는다")
    void simulations_with_unknown_move_partner_throws_before_deleting() {
        // given: 이동 장면의 동행자가 실제 유저가 아님
        given(userRepository.existsById(USER_ID)).willReturn(true);
        given(userRepository.countByIdIn(Set.of(999L))).willReturn(0L);
        SimulationsCommand command = new SimulationsCommand(USER_ID, DATE, List.of(
                moveScene("집", "학생 거리 탐앤탐스", List.of(999L))
        ), List.of(), List.of(), null);

        // when & then: 대화 상대와 같은 규칙으로 요청 오류로 끊고, 기존 결과는 그대로 둔다
        assertThatThrownBy(() -> simulationService.simulations(USER_ID, command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        then(sceneRepository).should(never()).findAllByUserIdAndDate(any(), any());
        then(sceneRepository).should(never()).saveAll(any());
    }

    private SimulationsSceneCommand moveScene(String fromPlace, String place, List<Long> with) {
        return new SimulationsMoveSceneCommand(
                DATE.atTime(11, 0), DATE.atTime(11, 10), "move", fromPlace, "P0258", place, "P0158", with,
                "walk", "sha256:89af", "카페로 향했다", null);
    }

    private SimulationsSceneCommand actionScene(String place) {
        return new SimulationsActionSceneCommand(
                DATE.atTime(9, 0), DATE.atTime(10, 0), "action", place, null, List.of(), "narration", null);
    }

    private SimulationsSceneCommand dialogueScene(String place) {
        return new SimulationsDialogueSceneCommand(
                DATE.atTime(11, 0), DATE.atTime(12, 0), "dialogue", place, null, List.of(PARTNER_ID), List.of());
    }

    private SimulationsSceneCommand dialogueScene(LocalDate date, int startHour, List<Long> with) {
        return new SimulationsDialogueSceneCommand(
                date.atTime(startHour, 0), date.atTime(startHour + 1, 0), "dialogue", "카페", null, with, List.of());
    }

    private void givenEmptyPreviousSimulation() {
        givenEmptyPreviousSimulation(DATE);
    }

    private void givenEmptyPreviousSimulation(LocalDate date) {
        given(userRepository.existsById(USER_ID)).willReturn(true);
        lenient().when(userRepository.countByIdIn(any()))
                .thenAnswer(invocation -> (long) invocation.<Collection<Long>>getArgument(0).size());
        given(sceneRepository.findAllByUserIdAndDate(USER_ID, date)).willReturn(List.of());
        given(questionRepository.findAllByUserIdAndDate(USER_ID, date)).willReturn(List.of());
        given(sceneRepository.saveAll(any())).willReturn(List.of());
        given(questionRepository.saveAll(any())).willReturn(List.of());
    }

    private SimulationsCommand simulationsCommand(Integer rapport) {
        return simulationsCommand(DATE, rapport);
    }

    private SimulationsCommand simulationsCommand(LocalDate date, Integer rapport) {
        return new SimulationsCommand(USER_ID, date, List.of(), List.of(), List.of(
                new SimulationsRelationshipCommand(PARTNER_ID, date.atTime(21, 0), rapport, "{}")
        ), null);
    }

    private IntimacyBonus bonus(int amount) {
        IntimacyBonus bonus = IntimacyBonus.create(USER_ID, PARTNER_ID, amount);
        ReflectionTestUtils.setField(bonus, "createdAt", Instant.now().minusSeconds(60));
        return bonus;
    }

    private Relationship relationship(Integer intimacy) {
        Relationship relationship = BeanUtils.instantiateClass(Relationship.class);
        ReflectionTestUtils.setField(relationship, "intimacy", intimacy);
        return relationship;
    }

    @Test
    @DisplayName("유저 기본 정보와 성향을 차원별로 묶어 반환하고 birthDate를 LocalDate로 변환한다")
    void persona_groups_elements_by_dimension() {
        given(entitlementReader.hasSimulationAccess(USER_ID)).willReturn(true);
        given(consentReader.hasAgreedAllRequired(USER_ID, PolicyKind.PARALLEL_ENTRY)).willReturn(true);
        // given: 성향 4건(관심사 2건 포함)을 가진 유저
        User user = user(USER_ID, "서", "성민", "컴퓨터공학과", "1999-03-21");
        ReflectionTestUtils.setField(user, "poolNumber", 3);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(personaElementRepository.findAllByUserIdOrderByIdAsc(USER_ID)).willReturn(List.of(
                personaElement(PersonaDimension.OPENNESS, "새로운 시도를 즐긴다"),
                personaElement(PersonaDimension.CONFLICT_STYLE, "직접 말하기보다 시간을 둔다"),
                personaElement(PersonaDimension.INTEREST, "등산"),
                personaElement(PersonaDimension.INTEREST, "재즈")
        ));

        // when: 페르소나 조회
        SimulationPersonaResult result = simulationService.persona(USER_ID, null);

        // then: 기본 정보가 매핑되고 성향은 차원별로 묶임
        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.familyName()).isEqualTo("서");
        assertThat(result.givenName()).isEqualTo("성민");
        assertThat(result.nickname()).isEqualTo("nickname");
        assertThat(result.gender()).isEqualTo(Gender.MALE);
        assertThat(result.organization()).isEqualTo("성균관대학교");
        assertThat(result.affiliation()).isEqualTo("컴퓨터공학과");
        assertThat(result.birthDate()).isEqualTo(LocalDate.of(1999, 3, 21));
        assertThat(result.poolNumber()).isEqualTo(3);
        assertThat(result.personaElements())
                .containsEntry(PersonaDimension.OPENNESS, List.of("새로운 시도를 즐긴다"))
                .containsEntry(PersonaDimension.CONFLICT_STYLE, List.of("직접 말하기보다 시간을 둔다"))
                .containsEntry(PersonaDimension.INTEREST, List.of("등산", "재즈"));
    }

    @Test
    @DisplayName("페르소나에 시뮬레이션할 날짜 기준 상대별 친밀도와, 그 계산에 쓴 기준 시각을 초 단위 KST 벽시계로 함께 담는다")
    void persona_includes_intimacies_with_the_same_intimacy_as_of_used_for_calculation() {
        // given: 시뮬레이션 권한과 동의가 있는 유저, 10/1 을 시뮬레이션할 때의 상대별 친밀도
        given(entitlementReader.hasSimulationAccess(USER_ID)).willReturn(true);
        given(consentReader.hasAgreedAllRequired(USER_ID, PolicyKind.PARALLEL_ENTRY)).willReturn(true);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID, "서", "성민", "컴퓨터공학과", "1999-03-21")));
        given(personaElementRepository.findAllByUserIdOrderByIdAsc(USER_ID)).willReturn(List.of());
        LocalDate date = LocalDate.of(2026, 10, 1);
        given(intimacyReader.readForSimulation(eq(USER_ID), eq(date), any(Instant.class)))
                .willReturn(Map.of(56L, 20, PARTNER_ID, 46));
        LocalDateTime before = KstTimes.now();

        // when: 페르소나 조회
        SimulationPersonaResult result = simulationService.persona(USER_ID, date);
        LocalDateTime after = KstTimes.now();

        // then: 상대 id 순으로 친밀도가 담긴다
        assertThat(result.intimacies()).containsExactly(
                new SimulationPersonaIntimacyResult(PARTNER_ID, 46),
                new SimulationPersonaIntimacyResult(56L, 20));

        // then: 기준 시각은 초 단위로 잘리고, 아직 커밋되지 않은 게임 점수를 놓치지 않도록 지금보다 5초 이상 앞선다
        assertThat(result.intimacyAsOf().getNano()).isZero();
        assertThat(result.intimacyAsOf()).isBeforeOrEqualTo(after.minusSeconds(5));
        assertThat(result.intimacyAsOf()).isAfter(before.minusSeconds(7));

        // then: 친밀도 계산에 쓴 시각과 내려준 시각이 정확히 같아야 AI가 돌려줄 때 짝이 맞는다
        then(intimacyReader).should().readForSimulation(USER_ID, date, KstTimes.toInstant(result.intimacyAsOf()));
    }

    @Test
    @DisplayName("AI 친밀도가 70 미만이어도 기준 시각 이후 게임 점수를 더해 70 이 되면 채팅방을 연다")
    void simulations_opens_chat_room_when_rapport_plus_game_bonus_reaches_best_friend() {
        // given: AI 친밀도 66, 기준 시각이 없는 결과라 지금까지 얻은 게임 점수 4 를 모두 더한다
        givenEmptyPreviousSimulation();
        given(intimacyReader.readBonuses(USER_ID, PARTNER_ID)).willReturn(new IntimacyBonuses(List.of(bonus(4))));

        // when: 시뮬레이션 결과 저장
        simulationService.simulations(USER_ID, simulationsCommand(66));

        // then: 갱신 시각이 이미 지났으므로 바로 연다
        then(chatRoomOpener).should().open(USER_ID, PARTNER_ID);
    }

    @Test
    @DisplayName("AI 친밀도와 게임 점수를 더해도 70 미만이면 채팅방을 열지 않는다")
    void simulations_does_not_open_chat_room_below_best_friend_with_game_bonus() {
        // given: 66 + 3 = 69
        givenEmptyPreviousSimulation();
        given(intimacyReader.readBonuses(USER_ID, PARTNER_ID)).willReturn(new IntimacyBonuses(List.of(bonus(3))));

        // when: 시뮬레이션 결과 저장
        simulationService.simulations(USER_ID, simulationsCommand(66));

        // then
        then(chatRoomOpener).should(never()).open(any(), any());
    }

    @Test
    @DisplayName("성향이 하나도 없으면 personaElements는 빈 Map이다")
    void persona_without_elements_returns_empty_map() {
        // given: 시뮬레이션 권한이 있고 성향이 한 건도 없는 유저
        given(entitlementReader.hasSimulationAccess(USER_ID)).willReturn(true);
        given(consentReader.hasAgreedAllRequired(USER_ID, PolicyKind.PARALLEL_ENTRY)).willReturn(true);
        // given: 성향이 한 건도 없는 유저
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID, "서", "성민", "컴퓨터공학과", "1999-03-21")));
        given(personaElementRepository.findAllByUserIdOrderByIdAsc(USER_ID)).willReturn(List.of());

        // when: 페르소나 조회
        SimulationPersonaResult result = simulationService.persona(USER_ID, null);

        // then: 성향은 빈 Map
        assertThat(result.personaElements()).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 유저면 USER_NOT_FOUND 예외가 발생하고 성향을 조회하지 않는다")
    void persona_user_not_found_throws() {
        // given: 해당 id의 유저 없음
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        // when & then: USER_NOT_FOUND 예외 발생 + 성향 조회 안 함
        assertThatThrownBy(() -> simulationService.persona(USER_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        then(purchaseService).should(never()).syncQuietly(any());
        then(personaElementRepository).should(never()).findAllByUserIdOrderByIdAsc(any());
    }

    @Test
    @DisplayName("페르소나 조회 시 시뮬레이션 권한이 없으면 SIMULATION_ACCESS_REQUIRED 예외가 발생한다")
    void persona_without_entitlement_throws() {
        // given: 정상 유저이지만 구독 권한이 없음
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID, "서", "성민", "컴퓨터공학과", "1999-03-21")));
        given(entitlementReader.hasSimulationAccess(USER_ID)).willReturn(false);

        // when & then: 파기(404)와 구분되는 403 코드로 실패하고 동의 여부나 페르소나를 읽지 않는다
        assertThatThrownBy(() -> simulationService.persona(USER_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SIMULATION_ACCESS_REQUIRED);

        then(purchaseService).should().syncQuietly(any());
        then(consentReader).should(never()).hasAgreedAllRequired(any(), any());
        then(personaElementRepository).should(never()).findAllByUserIdOrderByIdAsc(anyLong());
    }

    @Test
    @DisplayName("페르소나 조회 시 구독 중이어도 평행우주 입장 필수 약관에 동의하지 않았으면 SIMULATION_CONSENT_REQUIRED 예외가 발생한다")
    void persona_without_required_consent_throws() {
        // given: 구독 권한은 있지만 평행우주 입장 필수 약관 최신 버전에 동의하지 않음
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID, "서", "성민", "컴퓨터공학과", "1999-03-21")));
        given(entitlementReader.hasSimulationAccess(USER_ID)).willReturn(true);
        given(consentReader.hasAgreedAllRequired(USER_ID, PolicyKind.PARALLEL_ENTRY)).willReturn(false);

        // when & then: AI 서버가 구독 없음과 구분할 수 있도록 전용 403 코드로 실패하고 페르소나를 읽지 않는다
        assertThatThrownBy(() -> simulationService.persona(USER_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SIMULATION_CONSENT_REQUIRED);

        then(personaElementRepository).should(never()).findAllByUserIdOrderByIdAsc(anyLong());
    }

    @Test
    @DisplayName("탈퇴한 유저면 존재하지 않는 유저와 동일하게 USER_NOT_FOUND 예외가 발생한다")
    void persona_withdrawn_user_throws() {
        // given: 탈퇴 처리된 유저
        User withdrawn = user(USER_ID, "서", "성민", "컴퓨터공학과", "1999-03-21");
        ReflectionTestUtils.setField(withdrawn, "deletedAt", Instant.parse("2026-08-01T00:00:00Z"));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(withdrawn));

        // when & then: USER_NOT_FOUND 예외 발생 + 성향 조회 안 함
        assertThatThrownBy(() -> simulationService.persona(USER_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        then(purchaseService).should(never()).syncQuietly(any());
        then(personaElementRepository).should(never()).findAllByUserIdOrderByIdAsc(any());
    }

    @Test
    @DisplayName("탈퇴 유예 기간 중인 유저도 탈퇴한 유저와 동일하게 USER_NOT_FOUND 예외가 발생한다")
    void persona_withdrawal_requested_user_throws() {
        // given: 탈퇴를 신청했지만 아직 파기되지 않은 유저
        User pending = user(USER_ID, "서", "성민", "컴퓨터공학과", "1999-03-21");
        pending.requestWithdrawal(Duration.ofDays(15));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(pending));

        // when & then: USER_NOT_FOUND 예외 발생 + 성향 조회 안 함
        assertThatThrownBy(() -> simulationService.persona(USER_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        then(purchaseService).should(never()).syncQuietly(any());
        then(personaElementRepository).should(never()).findAllByUserIdOrderByIdAsc(any());
    }

    @Test
    @DisplayName("DB에 시뮬레이션 권한이 있으면 RevenueCat 동기화 없이 페르소나를 조회한다")
    void persona_with_entitlement_skips_sync() {
        // given: 정상 유저, DB에 권한 있음
        User user = user(USER_ID, "서", "성민", "컴퓨터공학과", "1999-03-21");
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(entitlementReader.hasSimulationAccess(USER_ID)).willReturn(true);
        given(consentReader.hasAgreedAllRequired(USER_ID, PolicyKind.PARALLEL_ENTRY)).willReturn(true);
        given(personaElementRepository.findAllByUserIdOrderByIdAsc(USER_ID)).willReturn(List.of());

        // when: 페르소나 조회
        simulationService.persona(USER_ID, null);

        // then: 외부 호출 없이 DB 권한만으로 통과
        then(purchaseService).should(never()).syncQuietly(any());
    }

    @Test
    @DisplayName("DB에 시뮬레이션 권한이 없으면 RevenueCat과 동기화한 뒤 다시 확인한다")
    void persona_without_entitlement_syncs_then_rechecks() {
        // given: DB에는 권한이 없지만 동기화 후에는 권한이 반영됨
        User user = user(USER_ID, "서", "성민", "컴퓨터공학과", "1999-03-21");
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(entitlementReader.hasSimulationAccess(USER_ID)).willReturn(false, true);
        given(consentReader.hasAgreedAllRequired(USER_ID, PolicyKind.PARALLEL_ENTRY)).willReturn(true);
        given(personaElementRepository.findAllByUserIdOrderByIdAsc(USER_ID)).willReturn(List.of());

        // when: 페르소나 조회
        simulationService.persona(USER_ID, null);

        // then: 동기화가 재확인보다 먼저 수행되고 조회가 성공함
        InOrder inOrder = inOrder(entitlementReader, purchaseService);
        inOrder.verify(entitlementReader).hasSimulationAccess(USER_ID);
        inOrder.verify(purchaseService).syncQuietly(user);
        inOrder.verify(entitlementReader).hasSimulationAccess(USER_ID);
    }

    private User user(Long id, String familyName, String givenName, String affiliation, String birthDate) {
        User user = User.create(
                "nickname", familyName, "familyNameHash", givenName, "givenNameHash", Gender.MALE,
                "성균관대학교", "organizationHash",
                affiliation, "affiliationHash", "20191234", "affiliationNumberHash",
                birthDate, "birthDateHash", "01012345678", "phoneNumberHash", "a@b.ac.kr", "emailHash", null, null, null, null);
        ReflectionTestUtils.setField(user, "id", id);

        return user;
    }

    private PersonaElement personaElement(PersonaDimension dimension, String explanation) {
        return PersonaElement.create(USER_ID, dimension, explanation, Instant.parse("2026-08-01T00:00:00Z"));
    }
}
