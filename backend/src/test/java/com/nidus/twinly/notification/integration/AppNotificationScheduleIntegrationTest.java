package com.nidus.twinly.notification.integration;

import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.entity.ScenePartner;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.notification.domain.AppNotificationScheduleType;
import com.nidus.twinly.notification.entity.AppNotificationSchedule;
import com.nidus.twinly.notification.repository.AppNotificationScheduleRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** 알림 예약이 실제 MySQL 에서 시각·중복·삭제 규칙대로 동작하는지 고정한다. */
class AppNotificationScheduleIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 14);
    private static final LocalDateTime KST_SCHEDULED = LocalDateTime.of(2026, 9, 14, 20, 10);
    private static final String EXPECTED_UTC = "2026-09-14 11:10:00";
    private static final String FRIEND = AppNotificationScheduleType.FRIEND.name();
    private static final String FIRST_MEETING = AppNotificationScheduleType.FIRST_MEETING.name();

    @Autowired
    AppNotificationScheduleRepository appNotificationScheduleRepository;

    @Autowired
    SceneRepository sceneRepository;

    @Autowired
    ScenePartnerRepository scenePartnerRepository;

    @Autowired
    EntityManager entityManager;

    private Long userId;
    private Long partnerUserId;

    @BeforeEach
    void setUp() {
        User user = saveUser();
        User partner = saveUser();
        userId = user.getId();
        partnerUserId = partner.getId();
    }

    @Test
    @DisplayName("KST 20:10 으로 예약하면 DB에는 UTC 11:10 으로 저장되고 같은 인스턴트로 읽힌다")
    void insertIfAbsent_stores_utc() {
        // given: KST 벽시계 20:10 을 인스턴트로 변환
        Instant scheduledAt = KstTimes.toInstant(KST_SCHEDULED);

        // when: 예약 저장
        appNotificationScheduleRepository.insertIfAbsent(userId, partnerUserId, FRIEND, DATE, scheduledAt);

        // then: 컬럼에는 9시간 당겨진 UTC 벽시계가 들어간다
        String raw = entityManager
                .createNativeQuery("SELECT CAST(scheduled_at AS CHAR) FROM app_notification_schedules WHERE user_id = :userId")
                .setParameter("userId", userId)
                .getSingleResult()
                .toString();
        assertThat(raw).startsWith(EXPECTED_UTC);

        // then: 다시 읽으면 원래 인스턴트와 날짜·종류가 그대로 복원된다
        assertThat(appNotificationScheduleRepository.findAll()).singleElement().satisfies(schedule -> {
            assertThat(schedule.getScheduledAt()).isEqualTo(scheduledAt);
            assertThat(schedule.getSimulationDate()).isEqualTo(DATE);
            assertThat(schedule.getType()).isEqualTo(AppNotificationScheduleType.FRIEND);
            assertThat(schedule.getSentAt()).isNull();
        });
    }

    @Test
    @DisplayName("도래 조회는 시각이 지난 미발송 예약만 이른 순서로 돌려준다")
    void due_query_returns_unsent_due_only() {
        // given: 지난 예약 둘(하나는 발송 완료)과 아직 남은 예약 하나
        Instant now = Instant.now();
        appNotificationScheduleRepository.insertIfAbsent(userId, partnerUserId, FRIEND, DATE, now.minusSeconds(60));
        appNotificationScheduleRepository.insertIfAbsent(userId, partnerUserId, FIRST_MEETING, DATE, now.minusSeconds(120));
        appNotificationScheduleRepository.insertIfAbsent(userId, partnerUserId, FRIEND, DATE.plusDays(1), now.plusSeconds(600));
        AppNotificationSchedule sent = appNotificationScheduleRepository.save(AppNotificationSchedule.create(
                partnerUserId, userId, AppNotificationScheduleType.FRIEND, DATE, now.minusSeconds(300)));
        sent.markSent(now);
        appNotificationScheduleRepository.saveAndFlush(sent);

        // when: 현재 시각으로 도래분 조회
        List<AppNotificationSchedule> due = appNotificationScheduleRepository
                .findAllBySentAtIsNullAndScheduledAtLessThanEqualOrderByScheduledAtAsc(now);

        // then: 이미 나간 건과 아직 시각이 안 된 건은 빠지고, 더 이른 첫 만남이 먼저 온다
        assertThat(due).extracting(AppNotificationSchedule::getType)
                .containsExactly(AppNotificationScheduleType.FIRST_MEETING, AppNotificationScheduleType.FRIEND);
    }

    @Test
    @DisplayName("같은 날짜로 이미 발송한 예약이 있으면 다시 예약해도 새로 만들지 않는다")
    void insertIfAbsent_keeps_sent_schedule() {
        // given: 발송까지 끝난 친구 예약
        Instant firstScheduledAt = KstTimes.toInstant(KST_SCHEDULED);
        appNotificationScheduleRepository.insertIfAbsent(userId, partnerUserId, FRIEND, DATE, firstScheduledAt);
        AppNotificationSchedule schedule = appNotificationScheduleRepository.findAll().getFirst();
        schedule.markSent(firstScheduledAt.plusSeconds(10));
        appNotificationScheduleRepository.saveAndFlush(schedule);

        // when: 같은 날짜 결과가 다시 들어와 더 늦은 시각으로 예약을 시도
        appNotificationScheduleRepository.insertIfAbsent(
                userId, partnerUserId, FRIEND, DATE, firstScheduledAt.plusSeconds(3600));
        entityManager.clear();

        // then: 행은 하나뿐이고 발송 표시가 그대로라 같은 알림이 두 번 나가지 않는다
        assertThat(appNotificationScheduleRepository.findAll()).singleElement().satisfies(kept -> {
            assertThat(kept.getScheduledAt()).isEqualTo(firstScheduledAt);
            assertThat(kept.getSentAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("미발송 삭제는 그 유저·그 날짜의 미발송 예약만 지운다")
    void deleteAllUnsent_deletes_only_unsent_of_the_date() {
        // given: 같은 날짜의 미발송·발송 완료 예약, 다른 날짜 예약, 다른 유저 예약
        Instant scheduledAt = KstTimes.toInstant(KST_SCHEDULED);
        appNotificationScheduleRepository.insertIfAbsent(userId, partnerUserId, FRIEND, DATE, scheduledAt);
        appNotificationScheduleRepository.insertIfAbsent(userId, partnerUserId, FRIEND, DATE.plusDays(1), scheduledAt);
        appNotificationScheduleRepository.insertIfAbsent(partnerUserId, userId, FRIEND, DATE, scheduledAt);
        AppNotificationSchedule sent = appNotificationScheduleRepository.save(AppNotificationSchedule.create(
                userId, partnerUserId, AppNotificationScheduleType.FIRST_MEETING, DATE, scheduledAt));
        sent.markSent(scheduledAt);
        appNotificationScheduleRepository.saveAndFlush(sent);

        // when: 같은 날짜 결과를 다시 저장하기 전의 정리
        appNotificationScheduleRepository.deleteAllUnsentByUserIdAndSimulationDate(userId, DATE);
        entityManager.clear();

        // then: 발송 이력은 중복 방지를 위해 남고, 다른 날짜와 다른 유저의 예약은 건드리지 않는다
        assertThat(appNotificationScheduleRepository.findAll())
                .extracting(AppNotificationSchedule::getUserId, AppNotificationSchedule::getType,
                        AppNotificationSchedule::getSimulationDate)
                .containsExactlyInAnyOrder(
                        tuple(userId, AppNotificationScheduleType.FIRST_MEETING, DATE),
                        tuple(userId, AppNotificationScheduleType.FRIEND, DATE.plusDays(1)),
                        tuple(partnerUserId, AppNotificationScheduleType.FRIEND, DATE));
    }

    @Test
    @DisplayName("이전 대화 조회는 기준 날짜보다 앞선 대화 장면의 상대만 돌려준다")
    void findPartnerUserIdsWithDialogueBeforeDate_counts_only_earlier_dialogues() {
        // given: 전날 대화한 상대, 전날 행동 장면만 함께한 상대, 기준 날짜 당일에 대화한 상대
        Long talkedBefore = saveUser().getId();
        Long actedBefore = saveUser().getId();
        Long talksToday = saveUser().getId();
        saveScene(Scene.createDialogue(userId, DATE.minusDays(1), "v1", "카페",
                DATE.minusDays(1).atTime(11, 0), DATE.minusDays(1).atTime(12, 0), "[]"), talkedBefore);
        saveScene(Scene.createAction(userId, DATE.minusDays(1), "v1", "학교",
                DATE.minusDays(1).atTime(9, 0), DATE.minusDays(1).atTime(10, 0), "narration", null), actedBefore);
        saveScene(Scene.createDialogue(userId, DATE, "v1", "카페",
                DATE.atTime(11, 0), DATE.atTime(12, 0), "[]"), talksToday);
        entityManager.flush();

        // when: 기준 날짜 이전에 대화한 상대 조회
        List<Long> alreadyMet = scenePartnerRepository.findPartnerUserIdsWithDialogueBeforeDate(
                userId, List.of(talkedBefore, actedBefore, talksToday), DATE);

        // then: 마주치기만 한 상대와 오늘 처음 대화하는 상대는 아직 첫 만남 대상이다
        assertThat(alreadyMet).containsExactly(talkedBefore);
    }

    private void saveScene(Scene scene, Long withUserId) {
        Scene saved = sceneRepository.save(scene);
        scenePartnerRepository.save(ScenePartner.create(saved.getId(), withUserId));
    }
}
