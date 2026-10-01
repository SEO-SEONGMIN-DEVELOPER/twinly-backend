package com.nidus.twinly.simulation.integration;

import com.google.api.client.json.gson.GsonFactory;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.Message;
import com.nidus.twinly.activity.repository.QuestionPartnerRepository;
import com.nidus.twinly.activity.repository.QuestionRepository;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.device.domain.DevicePlatform;
import com.nidus.twinly.device.entity.Device;
import com.nidus.twinly.device.repository.DeviceRepository;
import com.nidus.twinly.notification.domain.AppNotificationFeedType;
import com.nidus.twinly.notification.domain.AppNotificationScheduleType;
import com.nidus.twinly.notification.entity.AppNotificationFeed;
import com.nidus.twinly.notification.entity.AppNotificationSchedule;
import com.nidus.twinly.notification.repository.AppNotificationFeedRepository;
import com.nidus.twinly.notification.repository.AppNotificationScheduleRepository;
import com.nidus.twinly.notification.service.AppNotificationScheduleService;
import com.nidus.twinly.people.repository.EncounterPreferenceRepository;
import com.nidus.twinly.people.repository.EncounterRepository;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 예약 발송은 AppNotificationScheduleSender 가 REQUIRES_NEW 로 별도 커넥션에서 처리하고,
 * 푸시는 그 트랜잭션이 커밋된 뒤에야 나간다. 테스트 트랜잭션 안에서는 예약 행이 그 커넥션에 보이지 않고
 * 커밋 후 리스너도 돌지 않으므로, 이 클래스는 테스트 트랜잭션을 끄고 픽스처를 실제 커밋한 뒤 직접 정리한다.
 * 날짜를 내일로 잡아, 매분 도는 실제 스케줄러가 이 테스트의 예약을 먼저 집어 가지 않게 한다.
 * 지난 날짜를 쓰는 테스트는 실제 스케줄러가 먼저 보내도 성립하도록 발송 건수가 아니라 결과 상태만 단언한다.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SimulationScheduledNotificationIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate TOMORROW = KstTimes.today().plusDays(1);
    private static final LocalDate YESTERDAY = KstTimes.today().minusDays(1);
    private static final Instant FIRST_DIALOGUE_AT = KstTimes.toInstant(TOMORROW.atTime(11, 0));
    private static final Instant BECAME_FRIEND_AT = KstTimes.toInstant(TOMORROW.atTime(21, 0));

    @Autowired
    AppNotificationScheduleService appNotificationScheduleService;

    @Autowired
    AppNotificationScheduleRepository appNotificationScheduleRepository;

    @Autowired
    AppNotificationFeedRepository appNotificationFeedRepository;

    @Autowired
    EncounterPreferenceRepository encounterPreferenceRepository;

    @Autowired
    EncounterRepository encounterRepository;

    @Autowired
    RelationshipRepository relationshipRepository;

    @Autowired
    QuestionPartnerRepository questionPartnerRepository;

    @Autowired
    QuestionRepository questionRepository;

    @Autowired
    ScenePartnerRepository scenePartnerRepository;

    @Autowired
    SceneRepository sceneRepository;

    @Autowired
    DeviceRepository deviceRepository;

    @Test
    @DisplayName("내일 일어날 첫 대화와 친구 승격은 저장 시점에 알리지 않고 각자의 시각으로 예약된다")
    void simulations_schedules_instead_of_notifying_immediately() throws Exception {
        // given: 실제 커밋된 유저 둘
        User me = saveUser();
        User partner = saveUser();

        // when: 내일 하루치 결과를 미리 반영
        simulate(me, partner);

        // then: 첫 만남은 대화 장면 시작 시각, 친구는 관계가 갱신되는 시각으로 예약된다
        assertThat(appNotificationScheduleRepository.findAll())
                .extracting(AppNotificationSchedule::getUserId, AppNotificationSchedule::getPartnerUserId,
                        AppNotificationSchedule::getType, AppNotificationSchedule::getScheduledAt,
                        AppNotificationSchedule::getSentAt)
                .containsExactlyInAnyOrder(
                        tuple(me.getId(), partner.getId(), AppNotificationScheduleType.FIRST_MEETING, FIRST_DIALOGUE_AT, null),
                        tuple(me.getId(), partner.getId(), AppNotificationScheduleType.FRIEND, BECAME_FRIEND_AT, null));

        // then: 아직 일어나지 않은 일이므로 알림 목록에는 아무것도 없다
        assertThat(appNotificationFeedRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("첫 대화 시각이 되면 첫 만남 푸시만 나가고, 친구 알림은 자기 시각이 될 때까지 남아 있다")
    void sendDue_sends_first_meeting_push_only_at_dialogue_start() throws Exception {
        // given: 푸시를 받을 기기와 내일 하루치 예약
        User me = saveUser();
        User partner = saveUser();
        deviceRepository.save(Device.create(me.getId(), UUID.randomUUID(), DevicePlatform.IOS, "push-token-me"));
        stubFirebase();
        simulate(me, partner);

        // when: 첫 대화가 시작된 직후 시각으로 도래분 처리
        int sent = appNotificationScheduleService.sendDue(FIRST_DIALOGUE_AT.plusSeconds(1));

        // then: 첫 만남 한 건만 나가고, 알림 목록에는 남지 않는다
        assertThat(sent).isEqualTo(1);
        assertThat(appNotificationFeedRepository.findAll()).isEmpty();
        assertThat(sentAtByType()).containsOnlyKeys(AppNotificationScheduleType.FIRST_MEETING);

        // then: 푸시는 커밋 뒤 별도 스레드에서 나가므로 기다렸다가 내용을 확인한다
        String json = sentMessages(1).getFirst();
        assertThat(json).contains("\"type\":\"oneTimePushOnly\"");
        assertThat(json).contains(partner.displayNickname() + "님을 처음 만났어요.");
        assertThat(json).doesNotContain("appNotificationId");
    }

    @Test
    @DisplayName("친구가 된 시각이 되면 friend 피드가 생기고 푸시가 나가며, 같은 결과를 다시 저장해도 또 알리지 않는다")
    void sendDue_sends_friend_once_even_after_resimulation() throws Exception {
        // given: 푸시를 받을 기기와 내일 하루치 예약
        User me = saveUser();
        User partner = saveUser();
        deviceRepository.save(Device.create(me.getId(), UUID.randomUUID(), DevicePlatform.IOS, "push-token-me"));
        stubFirebase();
        simulate(me, partner);

        // when: 친구가 된 직후 시각으로 도래분 처리
        int sent = appNotificationScheduleService.sendDue(BECAME_FRIEND_AT.plusSeconds(1));

        // then: 앞선 첫 만남까지 두 건이 나가고, 친구 알림은 상대 프로필을 가리키는 피드로 남는다
        assertThat(sent).isEqualTo(2);
        assertThat(appNotificationFeedRepository.findAll())
                .extracting(AppNotificationFeed::getUserId, AppNotificationFeed::getType,
                        AppNotificationFeed::getTargetUserId, AppNotificationFeed::getSimulationDate)
                .containsExactly(tuple(me.getId(), AppNotificationFeedType.FRIEND, partner.getId(), TOMORROW));
        assertThat(sentMessages(2)).anySatisfy(json -> assertThat(json).contains("\"type\":\"friend\""));

        // when: 같은 날짜 결과가 다시 저장되고, 다시 도래분을 처리
        simulate(me, partner);
        int resent = appNotificationScheduleService.sendDue(BECAME_FRIEND_AT.plusSeconds(60));

        // then: 발송 이력이 남아 있어 새 예약이 생기지 않고, 피드도 한 건 그대로다
        assertThat(resent).isZero();
        assertThat(appNotificationScheduleRepository.findAll()).hasSize(2)
                .allSatisfy(schedule -> assertThat(schedule.getSentAt()).isNotNull());
        assertThat(appNotificationFeedRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("예약이 나가기 전에 결과가 바뀌어 다시 저장되면 이전 예약은 사라지고 새 결과대로만 예약된다")
    void simulations_replaces_unsent_schedules_on_resimulation() throws Exception {
        // given: 내일 하루치 예약
        User me = saveUser();
        User partner = saveUser();
        simulate(me, partner);

        // when: 대화도 친구 승격도 없는 결과로 같은 날짜를 다시 저장
        simulateNothing(me);

        // then: 일어나지 않게 된 일의 예약이 남아 발송되지 않는다
        assertThat(appNotificationScheduleRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("이미 지난 첫 대화와 친구 승격도 저장 뒤 첫 발송 주기에 한 번씩 나가고, 같은 날짜를 다시 저장해도 또 나가지 않는다")
    void sendDue_sends_past_notifications_once() throws Exception {
        // given: 푸시를 받을 기기
        User me = saveUser();
        User partner = saveUser();
        deviceRepository.save(Device.create(me.getId(), UUID.randomUUID(), DevicePlatform.IOS, "push-token-me"));
        stubFirebase();

        // when: 어제 하루치 결과가 뒤늦게 저장되고 도래분을 처리
        simulate(me, partner, YESTERDAY);
        appNotificationScheduleService.sendDue(Instant.now());

        // then: 저장 시점에 직접 보내지 않고, 일어난 시각 그대로 예약됐다가 곧바로 발송된다
        List<String> messages = sentMessages(2);
        assertThat(messages).anySatisfy(json -> assertThat(json).contains(partner.displayNickname() + "님을 처음 만났어요."));
        assertThat(messages).anySatisfy(json -> assertThat(json).contains("\"type\":\"friend\""));
        assertThat(appNotificationScheduleRepository.findAll())
                .extracting(AppNotificationSchedule::getType, AppNotificationSchedule::getScheduledAt)
                .containsExactlyInAnyOrder(
                        tuple(AppNotificationScheduleType.FIRST_MEETING, KstTimes.toInstant(YESTERDAY.atTime(11, 0))),
                        tuple(AppNotificationScheduleType.FRIEND, KstTimes.toInstant(YESTERDAY.atTime(21, 0))));
        assertThat(appNotificationScheduleRepository.findAll())
                .allSatisfy(schedule -> assertThat(schedule.getSentAt()).isNotNull());
        assertThat(appNotificationFeedRepository.findAll())
                .extracting(AppNotificationFeed::getType, AppNotificationFeed::getSimulationDate)
                .containsExactly(tuple(AppNotificationFeedType.FRIEND, YESTERDAY));

        // when: 같은 날짜 결과가 다시 저장되고 다시 도래분을 처리
        simulate(me, partner, YESTERDAY);
        appNotificationScheduleService.sendDue(Instant.now());

        // then: 발송 이력이 중복을 막아 예약·피드·푸시 모두 그대로다
        assertThat(appNotificationScheduleRepository.findAll()).hasSize(2);
        assertThat(appNotificationFeedRepository.findAll()).hasSize(1);
        then(firebaseMessaging).should(after(1_000).times(2)).sendEach(any());
    }

    @AfterEach
    void cleanUp() {
        // 롤백이 없으므로 직접 지운다. 순서는 FK 의존의 역방향(자식 → 부모)
        appNotificationScheduleRepository.deleteAll();
        appNotificationFeedRepository.deleteAll();
        deviceRepository.deleteAll();
        encounterPreferenceRepository.deleteAll();
        encounterRepository.deleteAll();
        relationshipRepository.deleteAll();
        questionPartnerRepository.deleteAll();
        questionRepository.deleteAll();
        scenePartnerRepository.deleteAll();
        sceneRepository.deleteAll();
        userRepository.deleteAll();
    }

    private Map<AppNotificationScheduleType, Instant> sentAtByType() {
        return appNotificationScheduleRepository.findAll().stream()
                .filter(schedule -> schedule.getSentAt() != null)
                .collect(Collectors.toMap(AppNotificationSchedule::getType, AppNotificationSchedule::getSentAt));
    }

    private void stubFirebase() throws Exception {
        // 스텁하지 않으면 목이 null을 돌려줘 FcmSender 가 응답을 훑다가 비동기 스레드에서 NPE 를 낸다
        BatchResponse batchResponse = mock(BatchResponse.class);
        given(batchResponse.getResponses()).willReturn(List.of());
        given(firebaseMessaging.sendEach(any())).willReturn(batchResponse);
    }

    @SuppressWarnings("unchecked")
    private List<String> sentMessages(int expectedCalls) throws Exception {
        ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
        then(firebaseMessaging).should(timeout(5_000).times(expectedCalls)).sendEach(captor.capture());

        return captor.getAllValues().stream()
                .flatMap(List::stream)
                .map(this::toJson)
                .toList();
    }

    private String toJson(Message message) {
        try {
            return GsonFactory.getDefaultInstance().toString(message);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void simulate(User me, User partner) throws Exception {
        simulate(me, partner, TOMORROW);
    }

    private void simulate(User me, User partner, LocalDate date) throws Exception {
        String payload = """
                {
                  "userId": "%d",
                  "date": "%s",
                  "scenes": [
                    {
                      "type": "dialogue",
                      "start": "%sT11:00:00",
                      "end": "%sT11:30:00",
                      "place": "카페",
                      "with": ["%d"],
                      "lines": [
                        {"t": "bubble", "userId": "%d", "text": "안녕", "occursAt": "%sT11:10:00"}
                      ]
                    }
                  ],
                  "questions": [],
                  "relationships": [
                    {
                      "partnerId": "%d",
                      "updateTime": "%sT21:00:00",
                      "rapport": 40,
                      "partnerModel": "model-v1"
                    }
                  ]
                }
                """.formatted(me.getId(), date, date, date, partner.getId(), partner.getId(), date,
                partner.getId(), date);

        request(me, payload);
    }

    private void simulateNothing(User me) throws Exception {
        String payload = """
                {
                  "userId": "%d",
                  "date": "%s",
                  "scenes": [],
                  "questions": [],
                  "relationships": []
                }
                """.formatted(me.getId(), TOMORROW);

        request(me, payload);
    }

    private void request(User me, String payload) throws Exception {
        mockMvc.perform(post("/internal/v1/users/{userId}/simulations", me.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }
}
