package com.nidus.twinly.notification.integration;

import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.notification.domain.AppNotificationScheduleType;
import com.nidus.twinly.notification.entity.AppNotificationSchedule;
import com.nidus.twinly.notification.repository.AppNotificationScheduleRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * V33 의 채우기 SQL 을 기존 데이터가 있는 상태에서 그대로 실행해, 코드의 판정 규칙과 같은 결과를 내는지 확인한다.
 * 서버 JVM 은 UTC 라서 장면·관계의 시각은 한국 벽시계 값 그대로 저장된다. 로컬 JVM(KST)으로 엔티티를 저장하면
 * 9시간 당겨진 값이 들어가 서버와 달라지므로, 픽스처는 서버에 저장되는 모양 그대로 SQL 문자열로 넣는다.
 */
class AppNotificationScheduleBackfillIntegrationTest extends AbstractIntegrationTest {

    private static final String MIGRATION = "db/migration/V33__add_app_notification_schedules.sql";
    private static final LocalDate TODAY = KstTimes.today();

    @Autowired
    AppNotificationScheduleRepository appNotificationScheduleRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManager entityManager;

    @Test
    @DisplayName("첫 만남: 쌍마다 가장 이른 대화 날짜의 가장 이른 장면으로 한 줄만 넣고, 지난 것은 보냄·남은 것은 미발송으로 둔다")
    void backfills_first_meetings() throws IOException {
        // given: 이미 만난 상대(이틀 전 두 번, 어제 한 번), 내일 처음 대화할 상대, 행동 장면만 함께한 상대, 본인이 낀 대화
        Long me = saveUser().getId();
        Long met = saveUser().getId();
        Long upcoming = saveUser().getId();
        Long actedOnly = saveUser().getId();
        dialogue(me, TODAY.minusDays(2), 11, met);
        dialogue(me, TODAY.minusDays(2), 9, met);
        dialogue(me, TODAY.minusDays(1), 8, met);
        action(me, TODAY.minusDays(2), 7, upcoming);
        dialogue(me, TODAY.plusDays(1), 15, upcoming);
        action(me, TODAY.minusDays(1), 10, actedOnly);
        dialogue(me, TODAY.minusDays(1), 13, me);
        entityManager.flush();

        // when: 채우기 SQL 실행
        runBackfill();

        // then: 이미 지난 첫 만남은 보냄으로, 아직 오지 않은 첫 만남은 그 시각의 미발송 예약으로 들어간다
        List<AppNotificationSchedule> schedules = schedulesOf(me, AppNotificationScheduleType.FIRST_MEETING);
        assertThat(schedules)
                .extracting(AppNotificationSchedule::getPartnerUserId, AppNotificationSchedule::getSimulationDate,
                        AppNotificationSchedule::getScheduledAt, schedule -> schedule.getSentAt() != null)
                .containsExactlyInAnyOrder(
                        tuple(met, TODAY.minusDays(2), kst(TODAY.minusDays(2), 9), true),
                        tuple(upcoming, TODAY.plusDays(1), kst(TODAY.plusDays(1), 15), false));
    }

    @Test
    @DisplayName("친구: 친밀도가 35 미만에서 35 이상으로 넘어간 날마다 관계 갱신 시각으로 넣고, 전부 보냄으로 둔다")
    void backfills_friends_as_sent() throws IOException {
        // given: 이틀 전 친구가 된 상대, 이전 기록 없이 바로 친구가 된 상대, 내려갔다 다시 올라온 상대, 기준 미달 상대
        Long me = saveUser().getId();
        Long steady = saveUser().getId();
        Long instant = saveUser().getId();
        Long again = saveUser().getId();
        Long acquaintance = saveUser().getId();
        relationship(me, steady, TODAY.minusDays(3), 20);
        relationship(me, steady, TODAY.minusDays(2), 40);
        relationship(me, steady, TODAY.minusDays(1), 50);
        relationship(me, instant, TODAY.plusDays(1), 36);
        relationship(me, again, TODAY.minusDays(3), 40);
        relationship(me, again, TODAY.minusDays(2), 30);
        relationship(me, again, TODAY.minusDays(1), 38);
        relationship(me, acquaintance, TODAY.minusDays(1), 34);
        entityManager.flush();

        // when: 채우기 SQL 실행
        runBackfill();

        // then: 기존 코드가 저장 시점에 이미 알렸으므로 미래 시각인 건까지 모두 보냄으로 들어간다
        List<AppNotificationSchedule> schedules = schedulesOf(me, AppNotificationScheduleType.FRIEND);
        assertThat(schedules)
                .extracting(AppNotificationSchedule::getPartnerUserId, AppNotificationSchedule::getSimulationDate,
                        AppNotificationSchedule::getScheduledAt)
                .containsExactlyInAnyOrder(
                        tuple(steady, TODAY.minusDays(2), kst(TODAY.minusDays(2), 21)),
                        tuple(instant, TODAY.plusDays(1), kst(TODAY.plusDays(1), 21)),
                        tuple(again, TODAY.minusDays(3), kst(TODAY.minusDays(3), 21)),
                        tuple(again, TODAY.minusDays(1), kst(TODAY.minusDays(1), 21)));
        assertThat(schedules).allSatisfy(schedule -> assertThat(schedule.getSentAt()).isNotNull());
    }

    private void runBackfill() throws IOException {
        String sql = new ClassPathResource(MIGRATION).getContentAsString(StandardCharsets.UTF_8);
        List<String> inserts = Arrays.stream(sql.split(";"))
                .map(String::strip)
                .filter(statement -> statement.startsWith("INSERT"))
                .toList();

        assertThat(inserts).hasSize(2);
        inserts.forEach(jdbcTemplate::execute);
        entityManager.clear();
    }

    private List<AppNotificationSchedule> schedulesOf(Long userId, AppNotificationScheduleType type) {
        return appNotificationScheduleRepository.findAll().stream()
                .filter(schedule -> schedule.getUserId().equals(userId) && schedule.getType() == type)
                .toList();
    }

    private Instant kst(LocalDate date, int hour) {
        return KstTimes.toInstant(date.atTime(hour, 0));
    }

    private void dialogue(Long userId, LocalDate date, int hour, Long withUserId) {
        scene(userId, date, hour, "DIALOGUE", withUserId);
    }

    private void action(Long userId, LocalDate date, int hour, Long withUserId) {
        scene(userId, date, hour, "ACTION", withUserId);
    }

    private void scene(Long userId, LocalDate date, int hour, String type, Long withUserId) {
        jdbcTemplate.update("""
                        INSERT INTO scenes (user_id, `date`, version, place, starts_at, ends_at, type)
                        VALUES (?, ?, 'v1', '카페', ?, ?, ?)
                        """,
                userId, date.toString(), wallClock(date, hour, 0), wallClock(date, hour, 30), type);
        Long sceneId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update("INSERT INTO scene_partners (scene_id, user_id) VALUES (?, ?)", sceneId, withUserId);
    }

    private void relationship(Long userId, Long partnerUserId, LocalDate date, int intimacy) {
        jdbcTemplate.update("""
                        INSERT INTO relationships (user_id, `date`, version, partner_user_id, intimacy, partner_model, update_time)
                        VALUES (?, ?, 'v1', ?, ?, 'model-v1', ?)
                        """,
                userId, date.toString(), partnerUserId, intimacy, wallClock(date, 21, 0));
    }

    private String wallClock(LocalDate date, int hour, int minute) {
        return "%s %02d:%02d:00".formatted(date, hour, minute);
    }
}
