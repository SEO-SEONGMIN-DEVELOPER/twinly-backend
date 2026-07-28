package com.nidus.twinly.main.integration;

import com.nidus.twinly.notification.repository.AppNotificationFeedRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MainIntegrationTest extends AbstractIntegrationTest {

    /** 활성 시즌으로 넣을 시즌 id. MainService는 활성 시즌 중 가장 최신 시즌을 현재 시즌으로 본다. */
    private static final long CURRENT_SEASON_ID = 1L;

    /** MySQL DATETIME(6) 리터럴 포맷. 문자열로 넣어야 드라이버의 타임존 변환 없이 UTC 벽시계 값이 그대로 저장된다. */
    private static final DateTimeFormatter DB_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    AppNotificationFeedRepository appNotificationFeedRepository;

    @Test
    @DisplayName("메인 탭 조회: 실제 시즌·알림 데이터를 관통해 진행률과 미읽음 개수를 반환한다")
    void mainTab_success_end_to_end() throws Exception {
        // given: 실제 유저 2명(조회자/알림 대상)과 현재 시즌, 조회자의 안읽은 알림 1건·읽은 알림 1건을 저장
        // (시즌 구간은 정수 나눗셈 경계에 걸리지 않도록 중앙에서 살짝 비껴 잡는다: 총 200일 중 101일 경과 → 50%)
        User me = saveUser();
        User target = saveUser();
        Instant now = Instant.now();
        insertSeason(CURRENT_SEASON_ID, now.minus(Duration.ofDays(101)), now.plus(Duration.ofDays(99)));
        insertUnreadNotification(me.getId(), target.getId());
        insertReadNotification(me.getId(), target.getId(), now.minus(Duration.ofHours(1)));

        // when: 조회자의 실제 액세스 토큰으로 메인 탭 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/main")
                .header("Authorization", bearer(me.getId())));

        // then: 200 반환 + 시즌 진행률·미읽음 개수가 실제 DB 상태를 반영한 JSON으로 내려온다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.season.seasonId").value(String.valueOf(CURRENT_SEASON_ID)))
                .andExpect(jsonPath("$.season.serverNow").isString())
                .andExpect(jsonPath("$.season.progress").value("50%"))
                .andExpect(jsonPath("$.unreadChatRoomCount").value(0))
                .andExpect(jsonPath("$.unreadNotificationCount").value(1));

        // then: 응답의 미읽음 알림 개수가 실제 DB 집계와 일치한다 (읽은 알림은 제외됨)
        assertThat(appNotificationFeedRepository.countByUserIdAndReadAtIsNull(me.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("인증 헤더가 없으면 401을 반환한다")
    void mainTab_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 메인 탭 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/main"));

        // then: 401 반환
        result.andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("현재 시즌으로 설정된 시즌 행이 없으면 500을 반환한다")
    void mainTab_without_current_season_returns_500() throws Exception {
        // given: 시즌 행을 만들지 않은 상태의 실제 유저 (각 테스트는 롤백되므로 seasons 테이블은 비어 있다)
        User me = saveUser();

        // when: 조회자의 실제 액세스 토큰으로 메인 탭 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/main")
                .header("Authorization", bearer(me.getId())));

        // then: MainService의 IllegalStateException이 500(INTERNAL_ERROR)으로 매핑된다
        result.andExpect(status().isInternalServerError());
    }

    /** seasons는 팩토리가 없으므로 네이티브 INSERT로 넣고, 현재 시즌이 되도록 is_active를 켠다. */
    private void insertSeason(long seasonId, Instant startedAt, Instant endedAt) {
        jdbcTemplate.update(
                "INSERT INTO seasons (id, started_at, ended_at, is_active) VALUES (?, ?, ?, 1)",
                seasonId, utc(startedAt), utc(endedAt));
    }

    private void insertUnreadNotification(Long userId, Long targetUserId) {
        jdbcTemplate.update("""
                INSERT INTO app_notification_feeds (user_id, title, body, type, target_kind, target_user_id)
                VALUES (?, '친구 요청', '친구 요청이 왔어요', 'FRIEND', 'PROFILE', ?)
                """, userId, targetUserId);
    }

    private void insertReadNotification(Long userId, Long targetUserId, Instant readAt) {
        jdbcTemplate.update("""
                INSERT INTO app_notification_feeds (user_id, title, body, type, target_kind, target_user_id, read_at)
                VALUES (?, '친구 요청', '친구 요청이 왔어요', 'FRIEND', 'PROFILE', ?, ?)
                """, userId, targetUserId, utc(readAt));
    }

    private static String utc(Instant instant) {
        return DB_DATETIME.format(LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
    }
}
