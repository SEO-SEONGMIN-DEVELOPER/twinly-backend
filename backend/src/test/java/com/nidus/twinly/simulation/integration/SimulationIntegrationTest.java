package com.nidus.twinly.simulation.integration;

import com.nidus.twinly.activity.entity.Question;
import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.repository.QuestionPartnerRepository;
import com.nidus.twinly.activity.repository.QuestionRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.notification.domain.AppNotificationScheduleType;
import com.nidus.twinly.notification.entity.AppNotificationSchedule;
import com.nidus.twinly.notification.repository.AppNotificationScheduleRepository;
import com.nidus.twinly.relationship.entity.Relationship;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SimulationIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 18);

    @Autowired
    SceneRepository sceneRepository;

    @Autowired
    ScenePartnerRepository scenePartnerRepository;

    @Autowired
    QuestionRepository questionRepository;

    @Autowired
    QuestionPartnerRepository questionPartnerRepository;

    @Autowired
    AppNotificationScheduleRepository appNotificationScheduleRepository;

    @Autowired
    RelationshipRepository relationshipRepository;

    @Test
    @DisplayName("같은 날짜로 다시 반영하면 이전 장면·질문과 그 자식 행까지 정리되고 새 결과로 교체된다")
    void simulations_replaces_previous_result_of_same_date() throws Exception {
        // given: 1차 반영. 픽스처를 직접 save하지 않고 API로 만들어야, 실제 코드가 만드는 자식 행 조합이 그대로 재현된다
        User me = saveUser();
        User partner = saveUser();
        simulate(me, partner, "1교시 교실", "같이 갈래?");

        List<Long> previousSceneIds = ids(sceneRepository.findAllByUserIdAndDate(me.getId(), DATE), Scene::getId);
        List<Long> previousQuestionIds = ids(questionRepository.findAllByUserIdAndDate(me.getId(), DATE), Question::getId);

        // given: 자식 행이 실제로 존재해야 FK 제약이 걸린다. 없으면 이 테스트는 아무것도 검증하지 못한다
        assertThat(scenePartnerRepository.findAllBySceneIdIn(previousSceneIds)).isNotEmpty();
        assertThat(questionPartnerRepository.findAllByQuestionIdIn(previousQuestionIds)).isNotEmpty();

        // when: 같은 날짜로 2차 반영
        simulate(me, partner, "점심 식당", "밥 먹자");

        // then: 이전 부모 행이 사라지고 자식 행도 함께 정리된다 (삭제 순서가 틀리면 FK 위반으로 여기서 드러난다)
        assertThat(sceneRepository.findAllById(previousSceneIds)).isEmpty();
        assertThat(questionRepository.findAllById(previousQuestionIds)).isEmpty();
        assertThat(scenePartnerRepository.findAllBySceneIdIn(previousSceneIds)).isEmpty();
        assertThat(questionPartnerRepository.findAllByQuestionIdIn(previousQuestionIds)).isEmpty();

        // then: 새 결과만 남고, 새 부모의 자식 행은 다시 만들어진다
        List<Scene> scenes = sceneRepository.findAllByUserIdAndDate(me.getId(), DATE);
        assertThat(scenes).extracting(Scene::getPlace).containsExactly("점심 식당");
        assertThat(scenePartnerRepository.findAllBySceneIdIn(ids(scenes, Scene::getId)))
                .extracting(scenePartner -> scenePartner.getUserId())
                .containsExactly(partner.getId());
    }

    @Test
    @DisplayName("이틀치 결과를 차례로 저장하면 첫 만남 알림은 처음 대화한 날에만, 새로 만난 상대에게만 예약된다")
    void simulations_schedules_first_meeting_only_for_first_dialogue_across_dates() throws Exception {
        // given: 첫날 한 상대와 대화한 결과가 저장돼 있다
        User me = saveUser();
        User partner = saveUser();
        User newcomer = saveUser();
        simulateDay(me, DATE, List.of(partner.getId()), partner.getId(), 20).andExpect(status().isOk());

        // when: 다음 날, 어제의 상대와 새로운 상대가 함께한 대화 결과를 저장
        simulateDay(me, DATE.plusDays(1), List.of(partner.getId(), newcomer.getId()), partner.getId(), 25)
                .andExpect(status().isOk());

        // then: 어제의 상대는 첫날 장면 시작 시각으로 한 번만, 새 상대는 둘째 날 장면 시작 시각으로 예약된다
        //       (이전 날짜의 대화 장면을 실제 쿼리로 찾지 못하면 어제의 상대가 둘째 날에도 예약돼 여기서 드러난다)
        assertThat(schedulesOf(me, AppNotificationScheduleType.FIRST_MEETING))
                .extracting(AppNotificationSchedule::getPartnerUserId, AppNotificationSchedule::getSimulationDate,
                        AppNotificationSchedule::getScheduledAt)
                .containsExactlyInAnyOrder(
                        tuple(partner.getId(), DATE, KstTimes.toInstant(DATE.atTime(9, 0))),
                        tuple(newcomer.getId(), DATE.plusDays(1), KstTimes.toInstant(DATE.plusDays(1).atTime(9, 0))));
    }

    @Test
    @DisplayName("처음 대화한 날에는 첫 대화 시작 시각의 친밀도 0 관계가 AI 관계와 같은 날짜로 함께 저장되고, 다음 날에는 넣지 않는다")
    void simulations_saves_zero_intimacy_relationship_only_on_first_dialogue_date() throws Exception {
        // given: 첫날 한 상대와 처음 대화한 결과가 저장돼 있다
        User me = saveUser();
        User partner = saveUser();
        User newcomer = saveUser();
        simulateDay(me, DATE, List.of(partner.getId()), partner.getId(), 20).andExpect(status().isOk());

        // when: 다음 날, 어제의 상대와 AI 관계가 오지 않은 새 상대가 함께한 대화 결과를 저장
        simulateDay(me, DATE.plusDays(1), List.of(partner.getId(), newcomer.getId()), partner.getId(), 25)
                .andExpect(status().isOk());

        // then: 같은 날짜에 0점 관계와 AI 관계가 공존하고(유니크 키에 갱신 시각이 들어가 있어야 저장된다),
        //       어제의 상대는 둘째 날 0점 관계 없이 AI 관계만, 새 상대는 0점 관계만 남는다
        assertThat(relationshipRepository.findAll().stream()
                .filter(relationship -> relationship.getUserId().equals(me.getId()))
                .toList())
                .extracting(Relationship::getPartnerUserId, Relationship::getDate, Relationship::getIntimacy,
                        Relationship::getUpdateTime, Relationship::getPartnerModel)
                .containsExactlyInAnyOrder(
                        tuple(partner.getId(), DATE, 0, DATE.atTime(9, 0), "아직 알게된 점이 없습니다."),
                        tuple(partner.getId(), DATE, 20, DATE.atTime(22, 0), "model-v1"),
                        tuple(partner.getId(), DATE.plusDays(1), 25, DATE.plusDays(1).atTime(22, 0), "model-v1"),
                        tuple(newcomer.getId(), DATE.plusDays(1), 0, DATE.plusDays(1).atTime(9, 0), "아직 알게된 점이 없습니다."));
    }

    @Test
    @DisplayName("친구 기준을 넘은 날에만 친구 알림이 예약되고, 다음 날 친밀도가 더 올라도 다시 예약되지 않는다")
    void simulations_schedules_friend_only_on_the_day_threshold_is_crossed() throws Exception {
        // given: 첫날 지인(20), 둘째 날 친구 기준(35)을 넘긴 결과가 저장돼 있다
        User me = saveUser();
        User partner = saveUser();
        simulateDay(me, DATE, List.of(partner.getId()), partner.getId(), 20).andExpect(status().isOk());
        simulateDay(me, DATE.plusDays(1), List.of(partner.getId()), partner.getId(), 40).andExpect(status().isOk());

        // when: 셋째 날, 이미 친구인 상대와 친밀도가 더 오른 결과를 저장
        simulateDay(me, DATE.plusDays(2), List.of(partner.getId()), partner.getId(), 55).andExpect(status().isOk());

        // then: 경계를 넘은 둘째 날의 관계 갱신 시각으로만 한 건 예약된다
        //       (직전 관계를 실제 쿼리로 읽지 못하면 셋째 날에도 예약돼 여기서 드러난다)
        assertThat(schedulesOf(me, AppNotificationScheduleType.FRIEND))
                .extracting(AppNotificationSchedule::getPartnerUserId, AppNotificationSchedule::getSimulationDate,
                        AppNotificationSchedule::getScheduledAt, AppNotificationSchedule::getSentAt)
                .containsExactly(tuple(partner.getId(), DATE.plusDays(1),
                        KstTimes.toInstant(DATE.plusDays(1).atTime(22, 0)), null));
    }

    @Test
    @DisplayName("경로의 userId와 본문의 userId가 다르면 400 INVALID_REQUEST를 반환하고 아무것도 저장·예약하지 않는다")
    void simulations_with_mismatched_user_id_returns_400() throws Exception {
        // given: 실제 유저 둘
        User me = saveUser();
        User other = saveUser();

        // when: 본문은 me 의 결과인데 경로는 다른 유저로 호출
        ResultActions result = mockMvc.perform(post("/internal/v1/users/{userId}/simulations", other.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload(me, DATE, List.of(other.getId()), other.getId(), 40)));

        // then: 400 반환 + 두 유저 어느 쪽에도 장면·관계·예약이 생기지 않는다
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(sceneRepository.findAllByUserIdAndDate(me.getId(), DATE)).isEmpty();
        assertThat(sceneRepository.findAllByUserIdAndDate(other.getId(), DATE)).isEmpty();
        assertThat(appNotificationScheduleRepository.findAll())
                .noneMatch(schedule -> schedule.getUserId().equals(me.getId()) || schedule.getUserId().equals(other.getId()));
    }

    @Test
    @DisplayName("존재하지 않는 유저의 결과를 저장하려 하면 404 USER_NOT_FOUND를 반환한다")
    void simulations_for_unknown_user_returns_404() throws Exception {
        // given: 상대는 실제 유저지만 저장 대상 유저는 없는 id
        User partner = saveUser();
        long unknownUserId = partner.getId() + 1_000_000L;
        String payload = payload(unknownUserId, DATE, List.of(partner.getId()), partner.getId(), 40);

        // when: 없는 유저의 결과 저장 API 호출
        ResultActions result = mockMvc.perform(post("/internal/v1/users/{userId}/simulations", unknownUserId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload));

        // then: 404 반환
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("상대 id가 실제 유저가 아니면 서버 오류가 아니라 400 INVALID_REQUEST를 반환하고 이전 결과를 그대로 둔다")
    void simulations_with_unknown_partner_returns_400_and_keeps_previous_result() throws Exception {
        // given: 같은 날짜로 이미 저장된 결과가 있다
        User me = saveUser();
        User partner = saveUser();
        simulateDay(me, DATE, List.of(partner.getId()), partner.getId(), 40).andExpect(status().isOk());
        long unknownPartnerId = partner.getId() + 1_000_000L;

        // when: 없는 유저를 상대로 담은 결과로 같은 날짜를 다시 저장
        ResultActions result = mockMvc.perform(post("/internal/v1/users/{userId}/simulations", me.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload(me.getId(), DATE, List.of(unknownPartnerId), unknownPartnerId, 40)));

        // then: 외래 키 위반(500)까지 가지 않고 요청 오류로 끊긴다
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        // then: 이전 장면과 예약은 지워지지 않고 남아 있다
        assertThat(sceneRepository.findAllByUserIdAndDate(me.getId(), DATE)).hasSize(1);
        assertThat(schedulesOf(me, AppNotificationScheduleType.FIRST_MEETING)).hasSize(1);
        assertThat(schedulesOf(me, AppNotificationScheduleType.FRIEND)).hasSize(1);
    }

    private List<AppNotificationSchedule> schedulesOf(User user, AppNotificationScheduleType type) {
        return appNotificationScheduleRepository.findAll().stream()
                .filter(schedule -> schedule.getUserId().equals(user.getId()) && schedule.getType() == type)
                .toList();
    }

    private ResultActions simulateDay(User me, LocalDate date, List<Long> with, Long partnerId, int rapport) throws Exception {
        return mockMvc.perform(post("/internal/v1/users/{userId}/simulations", me.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload(me, date, with, partnerId, rapport)));
    }

    private String payload(User me, LocalDate date, List<Long> with, Long partnerId, int rapport) {
        return payload(me.getId(), date, with, partnerId, rapport);
    }

    private String payload(Long userId, LocalDate date, List<Long> with, Long partnerId, int rapport) {
        String withJson = with.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(", ", "[", "]"));

        return """
                {
                  "userId": "%d",
                  "date": "%s",
                  "scenes": [
                    {
                      "type": "dialogue",
                      "start": "%sT09:00:00",
                      "end": "%sT09:30:00",
                      "place": "카페",
                      "with": %s,
                      "lines": [
                        {"t": "bubble", "userId": "%d", "text": "안녕", "occursAt": "%sT09:10:00"}
                      ]
                    }
                  ],
                  "questions": [],
                  "relationships": [
                    {
                      "partnerId": "%d",
                      "updateTime": "%sT22:00:00",
                      "rapport": %d,
                      "partnerModel": "model-v1"
                    }
                  ]
                }
                """.formatted(userId, date, date, date, withJson, partnerId, date, partnerId, date, rapport);
    }

    private void simulate(User me, User partner, String place, String line) throws Exception {
        String payload = """
                {
                  "userId": "%d",
                  "date": "2026-08-18",
                  "scenes": [
                    {
                      "type": "dialogue",
                      "start": "2026-08-18T09:00:00",
                      "end": "2026-08-18T09:30:00",
                      "place": "%s",
                      "with": ["%d"],
                      "lines": [
                        {"t": "bubble", "userId": "%d", "text": "%s", "occursAt": "2026-08-18T09:10:00"}
                      ]
                    }
                  ],
                  "questions": [
                    {
                      "time": "2026-08-18T21:00:00",
                      "qtype": "promise",
                      "partnerId": ["%d"],
                      "text": "오늘 어땠어?",
                      "options": ["좋았어", "그저 그랬어"]
                    }
                  ],
                  "relationships": [
                    {
                      "partnerId": "%d",
                      "updateTime": "2026-08-18T22:00:00",
                      "rapport": 20,
                      "partnerModel": "model-v1"
                    }
                  ]
                }
                """.formatted(me.getId(), place, partner.getId(), partner.getId(), line,
                partner.getId(), partner.getId());

        mockMvc.perform(post("/internal/v1/users/{userId}/simulations", me.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }

    private <T> List<Long> ids(List<T> entities, java.util.function.Function<T, Long> idGetter) {
        return entities.stream().map(idGetter).toList();
    }
}
